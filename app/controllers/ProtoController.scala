package controllers

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import jp.t2v.lab.play2.auth.AuthElement
import models.bittrex.AllMarketSummary
import models.db.{AccountRole, Tables}
import models.market.ClosePrice
import models.poloniex.{MarketMessage, MarketUpdate}
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, JsSuccess, Json, Reads}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.Controller
import services.DBService
import models.db.Tables.profile.api._
import services.actors.ExponentialMovingAverageActor.MarketCandleClosePrices

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal.RoundingMode
import models.db.Tables._


/**
  * All R&D projects should start here.
  *
  * @param database
  * @param messagesApi
  * @param ws
  * @param conf
  * @param system
  * @param materializer
  * @param context
  * @param webJarAssets
  */
class ProtoController  @Inject()(val database: DBService,
                                 val messagesApi: MessagesApi,
                                 ws: WSClient,
                                 conf: Configuration)
                                (implicit system: ActorSystem,
                                 materializer: Materializer,
                                 context: ExecutionContext,
                                 webJarAssets: WebJarAssets)

  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

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
          val bitcoin = polot.find( _.marketName == "USDT_BTC")

          // only care about btc markets
          val btcmarkets = polot.filter(t =>  t.marketName.startsWith("BTC"))
            .sortBy( tick => tick.info.baseVolume).reverse.map(t => {
            val percentChange = t.info.percentChange * 100
            t.copy(info = t.info.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })

          // find last differences here
          val markets = for {
            btxm <- btx
            polm <- btcmarkets
            if (btxm.name == polm.marketName)
          } yield {
            val diff = (polm.info.last - btxm.last).abs
            val f = "%1.8f".format(diff)

            println( s"${polm.marketName} bittrex last: ${btxm.last} poloniex last: ${polm.info.last} diff: $f")
            polm.marketName
          }

          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read polo and/or bittrex")
      }
    }
  }

}
