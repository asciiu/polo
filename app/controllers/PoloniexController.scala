package controllers

// external
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.wamp.{Wamp, messages}
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth.AuthElement
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.Controller
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.Future

// internal
import models.db.AccountRole
import models.poloniex.{Market, MarketStatus}
import services.DBService
import utils.poloniex.PoloniexWebSocketClient


@Singleton
class PoloniexController @Inject()(val database: DBService,
                         val messagesApi: MessagesApi,
                         val ws: WSClient,
                         implicit val system: ActorSystem,
                         implicit val context: ExecutionContext,
                         implicit val webJarAssets: WebJarAssets)
  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {
  import Wamp._
  import messages._

  val client = system.actorOf(Props[PoloniexWebSocketClient])
  IO(Wamp) ! Connect(client, url = "wss://api.poloniex.com")

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
          val btcmarkets = tickers.filter(t =>  t.ticker.startsWith("BTC") && t.status.baseVolume >= 100)
          val top10 = btcmarkets.sortBy( tick => tick.status.baseVolume).reverse
          //val top10 = tickers.sortBy( tick => tick.status.baseVolume).reverse
          Ok(views.html.poloniex.tickers(bitcoin, top10))
        case _ =>
          Ok(response.json)
      }
    }
  }
}