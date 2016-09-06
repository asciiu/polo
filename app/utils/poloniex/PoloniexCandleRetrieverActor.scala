package utils.poloniex

// external
import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import javax.inject.Inject

import models.market.ClosePrice
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.ExecutionContext.Implicits.global

// internal
import models.poloniex.{MarketCandle, PoloMarketCandle}
import services.CandleManagerActor.SetCandles
import services.ExponentialMovingAverageActor._


object PoloniexCandleRetrieverActor {
  trait CandleRetrieverMessage
  case class QueueMarket(marketName: String) extends CandleRetrieverMessage
  case object DequeueMarket extends CandleRetrieverMessage
}

/**
  * Created by bishop on 9/4/16
  * Retrieves candles for markets within the last 24 hours. All candles
  * are of 5 minute periods.
  */
class PoloniexCandleRetrieverActor @Inject()(ws: WSClient, conf: Configuration) extends Actor with ActorLogging {
  import PoloniexCandleRetrieverActor._
  import scala.concurrent.duration._
  import scala.language.postfixOps

  val eventBus = PoloniexEventBus()
  val url = conf.getString("poloniex.endpoint.public").getOrElse("https://poloniex.com/public")
  val marketQueue = scala.collection.mutable.Queue[String]()
  // 300 seconds = 5 minutes
  val candleLength = 300
  private var schedule: Option[Cancellable] = None

  // needed to convert poloniex long time (seconds) into DateTime
  val jodaDateReads = Reads[DateTime](js =>
    js.validate[Long].map[DateTime] { seconds =>
      new DateTime(seconds * 1000L)
    })

  val poloMarketReads: Reads[PoloMarketCandle] = (
    (JsPath \ "date").read[DateTime](jodaDateReads) and
      (JsPath \ "high").read[BigDecimal] and
      (JsPath \ "low").read[BigDecimal] and
      (JsPath \ "open").read[BigDecimal] and
      (JsPath \ "close").read[BigDecimal] and
      (JsPath \ "volume").read[BigDecimal] and
      (JsPath \ "quoteVolume").read[BigDecimal] and
      (JsPath \ "weightedAverage").read[BigDecimal]
    )(PoloMarketCandle.apply _)

  implicit val listReads: Reads[List[PoloMarketCandle]] = Reads { js =>
    JsSuccess(js.as[JsArray].value.map(j => j.validate[PoloMarketCandle](poloMarketReads).get).toList)
  }

  object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  }
  import Joda._

  private def startScheduler() = {
    if (!schedule.isDefined) {
      // periodically retrieve market data from poloniex
      // retrieving all candles for all markets is not possible with poloniex at the moment
      // therefore, we need to retrieve the candles for each market separately. Poloniex
      // will ban my IP if I make more than 6 calls per second. To be safe space the calls out
      // to 3 seconds.
      schedule = Some(context.system.scheduler.schedule(1 seconds, 3 seconds, self, DequeueMarket))
    }
  }

  private def stopScheduler() = {
    if (schedule.isDefined) {
      schedule.get.cancel()
      schedule = None
    }
  }

  override def preStart() = {
    eventBus.subscribe(self, "/market/added")
  }

  override def postStop() = {
    stopScheduler()
    eventBus.unsubscribe(self, "/market/added")
  }

  def receive: Receive = {
    case QueueMarket(marketName) =>
      marketQueue.enqueue(marketName)
      startScheduler()
    case DequeueMarket =>
      if (marketQueue.nonEmpty) {
        val marketName = marketQueue.dequeue()
        // TODO remove this condition
        if (marketName == "BTC_XMR") {
          // 24 hours ago in seconds
          // poloniex timestamps should be in UTC
          val timeStartSeconds = (new DateTime(DateTimeZone.UTC)).getMillis() / 1000L - 86400L
          ws.url(url)
            .withHeaders("Accept" -> "application/json")
            .withQueryString("command" -> "returnChartData")
            .withQueryString("currencyPair" -> marketName)
            .withQueryString("start" -> timeStartSeconds.toString)
            .withQueryString("end" -> "9999999999")
            .withQueryString("period" -> candleLength.toString)
            .withRequestTimeout(10000 milliseconds)
            .get().map { polo => {
              polo.json.validate[List[PoloMarketCandle]] match {
                case JsSuccess(candles, t) =>
                  // sorted by time with most recent first
                  val last24HrCandles = candles.map( cand => MarketCandle(cand) ).sortBy(_.time).reverse
                  eventBus.publish(MarketEvent("/market/candles", SetCandles(marketName, last24HrCandles)))

                  // publish closing prices for this market
                  val closingPrices = MarketCandleClosePrices(marketName, last24HrCandles.map( c => ClosePrice(c.time, c.close)))
                  eventBus.publish(MarketEvent("/market/prices", closingPrices))
                case x =>
                  log.error(s"could not retrieve candles for $marketName: ${x.toString}")
              }
            }
          }
        }
      } else {
        // there's noting to retrieve anymore no need to schedule further
        stopScheduler()
      }
  }
}
