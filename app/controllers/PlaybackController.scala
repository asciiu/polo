package controllers

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import jp.t2v.lab.play2.auth.AuthElement
import models.bittrex.AllMarketSummary
import models.db.{AccountRole, Tables}
import models.market.{ClosePrice, ExponentialMovingAverages}
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
class PlaybackController  @Inject()(val database: DBService,
                                    val messagesApi: MessagesApi,
                                    ws: WSClient,
                                    conf: Configuration)
                                   (implicit system: ActorSystem,
                                    materializer: Materializer,
                                    context: ExecutionContext,
                                    webJarAssets: WebJarAssets)

  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  val emas = new ExponentialMovingAverages()

  /**
    * Reads captured poloniex data from the DB and replays it in a test trading scenario.
    * @return
    */
  def playback() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    import Tables.PoloniexCandleRow

    // assumes all candle data is of 24 hour period
    val query = PoloniexCandle.filter(_.cryptoCurrency.startsWith("BTC_"))

    val result: Future[Seq[PoloniexCandleRow]] = database.runAsync(query.result)

    result.map { t =>
      val groupByMarket = t.groupBy(_.cryptoCurrency)

      for (marketName <- groupByMarket.keys) {
        // sort in descending time
        val candlesSorted = groupByMarket(marketName).sortBy(_.createdAt).reverse

        val closePrices = candlesSorted.map{ c => ClosePrice(c.createdAt, c.close)}.toList
        emas.setInitialMarketClosePrices(marketName, closePrices)
      }
      Ok("playback info here")
    }
  }
}
