package controllers

// external
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}

import jp.t2v.lab.play2.auth.AuthElement
import models.bittrex.AllMarketSummary
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.{Controller, WebSocket}
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.Configuration

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode

// internal
import models.db.AccountRole
import models.poloniex.{Market, MarketStatus}
import services.DBService
import utils.poloniex.{PoloniexCandleCreatorActor, PoloniexEventBus, PoloniexWebSocketSupervisor}

@Singleton
class PoloniexController @Inject()(val database: DBService,
                                   val messagesApi: MessagesApi,
                                   ws: WSClient,
                                   conf: Configuration,
                                   lifecycle: ApplicationLifecycle)
                                  (implicit system: ActorSystem,
                                   materializer: Materializer,
                                   context: ExecutionContext,
                                   webJarAssets: WebJarAssets)
  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  implicit val marketStatus = Json.format[MarketStatus]
  implicit val marketWrite = Json.writes[Market]
  implicit val marketRead: Reads[List[Market]] = Reads( js =>
    JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
      Market(ticker._1, ticker._2.as[MarketStatus])
    }.toList))

  conf.getString("poloniex.websocket") match {
    case Some(url) =>
      val websocket = system.actorOf(PoloniexWebSocketSupervisor.props(url), "poloniex-web-supervisor")
      val candleCreator = system.actorOf(PoloniexCandleCreatorActor.props(), "Poloniex-candle-creator")

      lifecycle.addStopHook{ () =>
        // gracefully stop these actors
        websocket ! PoisonPill
        candleCreator ! PoisonPill
        Future.successful()
      }
    case None =>
  }

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
        case update: Market if update.name.startsWith("BTC") || update.name == "USDT_BTC" =>
          out ! Json.toJson(update).toString
      }
    }

    ActorFlow.actorRef(out => Props(new BrowserActor(out)))
  }

  def markets() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    implicit val marketStatus = Json.reads[MarketStatus]
    implicit val marketRead: Reads[List[Market]] = Reads( js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        Market(ticker._1, ticker._2.as[MarketStatus])
      }.toList))

    // poloniex markets
    val poloniexRequest: WSRequest = ws.url("https://poloniex.com/public?command=returnTicker")
        .withHeaders("Accept" -> "application/json")
        .withRequestTimeout(10000.millis)

    poloniexRequest.get().map { polo =>
      polo.json.validate[List[Market]] match {
        case JsSuccess(tickers, t) =>
          // Bitcoin price
          val bitcoin = tickers.find( _.name == "USDT_BTC")

          // only care about btc markets
          val btcmarkets = tickers.filter(t =>  t.name.startsWith("BTC"))
            .sortBy( tick => tick.status.baseVolume).reverse.map(t => {

            val percentChange = t.status.percentChange * 100
            t.copy(status = t.status.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })
          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read poloniex market")
      }
    }
  }

  def arbiter() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    import models.bittrex.Bittrex._

    implicit val marketStatus = Json.reads[MarketStatus]
    implicit val marketRead: Reads[List[Market]] = Reads( js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        Market(ticker._1, ticker._2.as[MarketStatus])
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

      (btrx.json.validate[AllMarketSummary], polo.json.validate[List[Market]]) match {
        case (JsSuccess(bitrxt, t1), JsSuccess(polot, t2)) =>
          // bittrex markets
          val btx = bitrxt.result.map{ b => b.copy(name = b.name.replace("-", "_"))}.filter( b => b.name.startsWith("BTC"))

          // Bitcoin price
          val bitcoin = polot.find( _.name == "USDT_BTC")

          // only care about btc markets
          val btcmarkets = polot.filter(t =>  t.name.startsWith("BTC"))
            .sortBy( tick => tick.status.baseVolume).reverse.map(t => {
            val percentChange = t.status.percentChange * 100
            t.copy(status = t.status.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })

          // find last differences here
          val markets = for {
            btxm <- btx
            polm <- btcmarkets
            if (btxm.name == polm.name)
          } yield {
            val diff = (polm.status.last - btxm.last).abs
            val f = "%1.8f".format(diff)

            println( s"${polm.name} bittrex last: ${btxm.last} poloniex last: ${polm.status.last} diff: $f")
            polm.name
          }

          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read polo and/or bittrex")
      }
    }
  }
}
