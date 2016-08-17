package controllers

// external
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}

import jp.t2v.lab.play2.auth.AuthElement
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
      system.actorOf(PoloniexWebSocketSupervisor.props(url), "poloniex-web-supervisor")
      system.actorOf(PoloniexCandleCreatorActor.props(), "Poloniex-candle-creator")
    case None =>
  }

  def socket() = WebSocket.accept[String, String] { request =>
    class BrowserActor(out: ActorRef) extends Actor {
      val eventBus = PoloniexEventBus()

      override def preStart() = {
        eventBus.subscribe(self, "/market/update")
      }

      def receive = {
        case update: Market if update.ticker.startsWith("BTC") =>
          out ! Json.toJson(update).toString
      }
    }

    ActorFlow.actorRef(out => Props(new BrowserActor(out)))
  }

  def tickers() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>

    implicit val marketStatus = Json.reads[MarketStatus]
    implicit val marketRead: Reads[List[Market]] = Reads( js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        Market(ticker._1, ticker._2.as[MarketStatus])
      }.toList))

    val request: WSRequest = ws.url("https://poloniex.com/public?command=returnTicker")
    val complexRequest: WSRequest =
      request.withHeaders("Accept" -> "application/json")
        .withRequestTimeout(10000.millis)

    val futureResponse: Future[WSResponse] = complexRequest.get()

    futureResponse.map{ response =>
      //Ok(response.json.validate[List[Market]].toString)
      response.json.validate[List[Market]] match {
        case JsSuccess(tickers, t) =>
          // Bitcoin price
          val bitcoin = tickers.find( _.ticker == "USDT_BTC")

          // only care about btc markets
          val btcmarkets = tickers.filter(t =>  t.ticker.startsWith("BTC"))
            .sortBy( tick => tick.status.baseVolume).reverse.map(t => t.copy(status = t.status.copy(percentChange = t.status.percentChange.setScale(2, RoundingMode.CEILING))))
          Ok(views.html.poloniex.tickers(bitcoin, btcmarkets))
        case _ =>
          Ok(response.json)
      }
    }
  }
}
