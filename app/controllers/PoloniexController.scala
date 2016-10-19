package controllers

// external
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import akka.stream.Materializer
import javax.inject.{Inject, Named, Singleton}

import jp.t2v.lab.play2.auth.AuthElement
import models.poloniex.PoloniexEventBus
import models.poloniex.trade.PoloniexTradeClient
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
import models.market.{EMA, MarketCandle}
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
    * Sends market updates to all connected clients.
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
        case update: MarketUpdate if update.marketName.startsWith("BTC") =>
          val percentChange = update.info.percentChange * 100
          val ud = update.copy(info = update.info.copy(percentChange =
            percentChange.setScale(2, RoundingMode.CEILING)))
          out ! Json.toJson(ud).toString
        case update: MarketUpdate if update.marketName == "USDT_BTC" =>
          out ! Json.toJson(update).toString
      }
    }

    ActorFlow.actorRef(out => Props(new BrowserActor(out)))
  }

  def startCapture() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    candleActorRef ! StartCapture
    Future.successful(Ok("ok"))
  }

  def endCapture() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    candleActorRef ! EndCapture
    Future.successful(Ok("ok"))
  }

  /**
    * Displays all poloniex markets.
    */
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
          val bitcoin = tickers.find( _.marketName == "USDT_BTC")

          // only care about btc markets
          // order by base volume - btc vol
          val btcmarkets = tickers.filter(t =>  t.marketName.startsWith("BTC"))
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

  /**
    * The latest market data.
    *
    * @param marketName
    * @return
    */
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


  /**
    * Returns candle data for the following market.
    *
    * @param marketName
    */
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
          // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
          c.time.toEpochSecond() * 1000L - 2.16e+7,
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

  /**
    * Returns the latest candle for a market.
    *
    * @param marketName
    */
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

      val info = candle match {
        case Some(c) if averages.length == 2 =>
            Json.arr(
              // TODO UTF offerset should come from client
              c.time.toEpochSecond() * 1000L - 2.16e+7,
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
}
