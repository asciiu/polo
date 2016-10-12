package controllers

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import jp.t2v.lab.play2.auth.AuthElement
import models.bittrex.AllMarketSummary
import models.db.{AccountRole, Tables}
import models.market.{ClosePrice, ExponentialMovingAverages, MarketCandles, VolumeMatrix}
import models.poloniex.{MarketCandle, MarketMessage, MarketUpdate}
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

  val allEmas = new ExponentialMovingAverages()
  val allCandles = new MarketCandles()
  val allVolumes = new VolumeMatrix()

  implicit def convertMessageRow(row: PoloniexMessageRow): MarketUpdate = {
    val msg = MarketMessage(0, row.last, row.lowestAsk, row.highestBid,
      row.percentChange, row.baseVolume, row.quoteVolume, row.isFrozen.toString, row.high24hr,
      row.low24hr)
    MarketUpdate(row.cryptoCurrency, msg)
  }
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
        val sortedRows = groupByMarket(marketName).sortBy(_.createdAt).reverse

        val candles =  sortedRows.map(MarketCandle(_)).toList
        allCandles.appendCandles(marketName, candles)

        val closePrices = sortedRows.map{ c => ClosePrice(c.createdAt, c.close)}.toList
        allEmas.setInitialMarketClosePrices(marketName, closePrices)
      }

      // stream these
      val query2 = PoloniexMessage.filter(_.cryptoCurrency.startsWith("BTC_")).sortBy(_.createdAt)
      database.runAsync(query2.result).map { updates =>

        // play back all updates
        for( update <- updates) {

          val time = utils.Misc.roundDateToMinute(update.createdAt, 5)
          allCandles.updateMarket(update, time){ closePrice =>
            allEmas.update(closePrice.marketName, closePrice.close)
          }
          allEmas.update(update.cryptoCurrency, ClosePrice(time, update.last))

          // check the cross here

        }
      }
      Ok("playback info here")
    }
  }
}
