package controllers

// external
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import akka.stream.Materializer
import javax.inject.{Inject, Named, Singleton}

import jp.t2v.lab.play2.auth.AuthElement
import models.poloniex.{PoloniexEventBus, PoloniexTradeClient}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{Controller, RequestHeader, WebSocket}
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.Configuration
import org.joda.time.format.DateTimeFormat

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

// internal
import models.market.PeriodVolume
import models.db.AccountRole
import models.poloniex.{MarketUpdate, MarketMessage}
import services.DBService
import models.bittrex.AllMarketSummary
import models.market.EMA
import models.poloniex.MarketCandle
import services.actors.TradeActor.GetLatestMessage
import services.actors.VolumeTrackerActor.{GetVolume, GetVolumes}
import services.actors.{CandleManagerActor, ExponentialMovingAverageActor, TradeActor}
import ExponentialMovingAverageActor._
import CandleManagerActor._

@Singleton
class PoloniexController @Inject()(val database: DBService,
                                   val messagesApi: MessagesApi,
                                   ws: WSClient,
                                   conf: Configuration,
                                   @Named("candle-actor") candleActorRef: ActorRef,
                                   @Named("ema-actor") movingAveragesActor: ActorRef,
                                   @Named("polo-candle-retriever") candleRetrieverActor: ActorRef,
                                   @Named("polo-websocket-client") websocketClient: ActorRef,
                                   @Named("trade-actor") tradeActor: ActorRef,
                                   @Named("volume-actor") volumeActor: ActorRef)
                                  (implicit system: ActorSystem,
                                   materializer: Materializer,
                                   context: ExecutionContext,
                                   webJarAssets: WebJarAssets)
  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  implicit val marketStatus = Json.format[MarketMessage]
  implicit val marketWrite = Json.writes[MarketUpdate]
  implicit val marketRead: Reads[List[MarketUpdate]] = Reads(js =>
    JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
      MarketUpdate(ticker._1, ticker._2.as[MarketMessage])
    }.toList))

  val tradeClient = new PoloniexTradeClient(
    conf.getString("poloniex.apiKey").getOrElse(""),
    conf.getString("poloniex.secret").getOrElse(""),
    conf,
    ws
  )

  /**
    * Websocket for clients that sends market updates.
    */
  def socket() = WebSocket.accept[String, String] { request =>

    // each client will be served by this actor
    class BrowserActor(out: ActorRef) extends Actor {
      val eventBus = PoloniexEventBus()

      override def preStart() = {
        eventBus.subscribe(self, "/market/update")
      }

      def receive = {
        // send updates from Bitcoin markets only
        case update: MarketUpdate if update.name.startsWith("BTC") =>
          val percentChange = update.info.percentChange * 100
          val ud = update.copy(info = update.info.copy(percentChange =
            percentChange.setScale(2, RoundingMode.CEILING)))
          out ! Json.toJson(ud).toString
        case update: MarketUpdate if update.name == "USDT_BTC" =>
          out ! Json.toJson(update).toString
      }
    }

    ActorFlow.actorRef(out => Props(new BrowserActor(out)))
  }

  def markets() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    implicit val marketStatus = Json.reads[MarketMessage]
    implicit val marketRead: Reads[List[MarketUpdate]] = Reads(js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        MarketUpdate(ticker._1, ticker._2.as[MarketMessage])
      }.toList))

    // poloniex markets
    val poloniexRequest: WSRequest = ws.url("https://poloniex.com/public?command=returnTicker")
        .withHeaders("Accept" -> "application/json")
        .withRequestTimeout(10000.millis)

    poloniexRequest.get().map { polo =>
      polo.json.validate[List[MarketUpdate]] match {
        case JsSuccess(tickers, t) =>
          // Bitcoin price
          val bitcoin = tickers.find( _.name == "USDT_BTC")

          // only care about btc markets
          // order by base volume - btc vol
          val btcmarkets = tickers.filter(t =>  t.name.startsWith("BTC"))
            .sortBy( tick => tick.info.baseVolume).reverse.map(t => {

            // change percent format from decimal
            val percentChange = t.info.percentChange * 100
            //val name = t.name.replace("BTC_", "")
            t.copy(info = t.info.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })
          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read poloniex market")
      }
    }
  }

  def market(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    implicit val timeout = Timeout(5 seconds)

    (tradeActor ? GetLatestMessage(marketName)).mapTo[Option[MarketMessage]].map { msg =>
      msg match {
        case Some(m) =>
          // change percent format from decimal
          val percentChange = m.percentChange * 100
          //val name = t.name.replace("BTC_", "")
          val normalized = m.copy(percentChange =
            percentChange.setScale(2, RoundingMode.CEILING))

          Ok(views.html.poloniex.market(loggedIn, marketName, normalized))
        case None =>
          NotFound(views.html.errors.notFound(request))
      }
    }
  }

  // returns locally stored candles
  def candles(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    import CandleManagerActor._
    implicit val timeout = Timeout(5 seconds)

    for {
      candles <- (candleActorRef ? GetCandles(marketName)).mapTo[List[MarketCandle]]
      movingAverages <- (movingAveragesActor ? GetMovingAverages(marketName)).mapTo[List[(Int, List[EMA])]]
      volume24hr <- (volumeActor ? GetVolumes(marketName)).mapTo[List[PeriodVolume]]
    } yield {
      val l = candles.map { c =>
        Json.arr(
          // TODO UTF offerset should come from client
          c.time.getMillis - (6*3.6e+6),
          c.open,
          c.high,
          c.low,
          c.close,
          movingAverages(0)._2.find( avg => c.time.equals(avg.time)).getOrElse(EMA(c.time, 0)).ema,
          movingAverages(1)._2.find( avg => c.time.equals(avg.time)).getOrElse(EMA(c.time, 0)).ema,
          volume24hr.find( vol => c.time.equals(vol.time)).getOrElse(PeriodVolume(c.time, 0)).btcVolume.setScale(2, RoundingMode.DOWN)
        )
      }

      Ok(Json.toJson(l))
    }
  }

  // returns latest candle
  def latestCandle(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    import CandleManagerActor._
    implicit val timeout = Timeout(5 seconds)

    // TODO this will fail if the first future returns a None
    for {
      candle <- (candleActorRef ? GetLastestCandle(marketName)).mapTo[Option[MarketCandle]]
      averages <- (movingAveragesActor ? GetMovingAverage(marketName, candle.get.time)).mapTo[List[(Int, BigDecimal)]]
      volume24hr <- (volumeActor ? GetVolume(marketName, candle.get.time)).mapTo[PeriodVolume]
    } yield {
      val df = DateTimeFormat.forPattern("MMM dd HH:mm")

      // TODO UTF offerset should come from client
      val info = candle match {
        case Some(c) if averages.length == 2 =>
            Json.arr(
              c.time.getMillis() - (6*3.6e+6),
              c.open,
              c.high,
              c.low,
              c.close,
              averages(0)._2,
              averages(1)._2,
              volume24hr.btcVolume.setScale(2, RoundingMode.DOWN)
            )
        case _ =>
          Json.arr()
      }

      Ok(Json.toJson(info))
    }
  }


  // Prototyping an arbitrage scenario between Polo and bittrex
  // needs to be implemented.
  def arbiter() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    import models.bittrex.Bittrex._

    implicit val marketStatus = Json.reads[MarketMessage]
    implicit val marketRead: Reads[List[MarketUpdate]] = Reads(js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        MarketUpdate(ticker._1, ticker._2.as[MarketMessage])
      }.toList))

    // bittrex markets
    val bittrexRequest: WSRequest = ws.url("https://bittrex.com/api/v1.1/public/getmarketsummaries")
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(1000.millis)

    // poloniex markets
    val poloniexRequest: WSRequest = ws.url("https://poloniex.com/public?command=returnTicker")
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(10000.millis)

    for {
      polo <- poloniexRequest.get()
      btrx <- bittrexRequest.get()
    } yield {

      (btrx.json.validate[AllMarketSummary], polo.json.validate[List[MarketUpdate]]) match {
        case (JsSuccess(bitrxt, t1), JsSuccess(polot, t2)) =>
          // bittrex markets
          val btx = bitrxt.result.map{ b => b.copy(name = b.name.replace("-", "_"))}.filter( b => b.name.startsWith("BTC"))

          // Bitcoin price
          val bitcoin = polot.find( _.name == "USDT_BTC")

          // only care about btc markets
          val btcmarkets = polot.filter(t =>  t.name.startsWith("BTC"))
            .sortBy( tick => tick.info.baseVolume).reverse.map(t => {
            val percentChange = t.info.percentChange * 100
            t.copy(info = t.info.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })

          // find last differences here
          val markets = for {
            btxm <- btx
            polm <- btcmarkets
            if (btxm.name == polm.name)
          } yield {
            val diff = (polm.info.last - btxm.last).abs
            val f = "%1.8f".format(diff)

            println( s"${polm.name} bittrex last: ${btxm.last} poloniex last: ${polm.info.last} diff: $f")
            polm.name
          }

          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read polo and/or bittrex")
      }
    }
  }
}
