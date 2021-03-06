package services.actors

// external
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}

import models.market.MarketStructures.{Candles, ClosePrice}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import services.DBService

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

// internal
import models.market.MarketCandle
import models.poloniex.{MarketEvent, PoloMarketCandle, PoloniexEventBus}


object PoloniexCandleRetrieverService {

  def props(ws: WSClient, conf: Configuration)(implicit context: ExecutionContext): Props =
    Props(new PoloniexCandleRetrieverService(ws, conf))

  trait CandleRetrieverMessage
  case class QueueMarket(marketName: String) extends CandleRetrieverMessage
  case object DequeueMarket extends CandleRetrieverMessage
}

/**
  * Created by bishop on 9/4/16
  * Retrieves candles for markets within the last 24 hours. All candles
  * are of 5 minute periods.
  */
class PoloniexCandleRetrieverService (ws: WSClient, conf: Configuration)(implicit ctx: ExecutionContext)
  extends Actor with ActorLogging {

  import PoloniexCandleRetrieverService._

  import scala.concurrent.duration._
  import scala.language.postfixOps

  val eventBus = PoloniexEventBus()
  val url = conf.getString("poloniex.url.public").getOrElse("https://poloniex.com/public")
  val marketQueue = scala.collection.mutable.Queue[String]()
  val periodMinutes = 5
  // 300 seconds = 5 minutes
  val candleLength = 300
  private var schedule: Option[Cancellable] = None

  // needed to convert poloniex long time (seconds) into DateTime
  val dateReads = Reads[OffsetDateTime](js =>
    js.validate[Long].map[OffsetDateTime] { seconds =>
      // all timestamps are in seconds UTC time
      OffsetDateTime.ofInstant(Instant.ofEpochSecond(seconds),ZoneOffset.UTC)
    })

  val poloMarketReads: Reads[PoloMarketCandle] = (
    (JsPath \ "date").read[OffsetDateTime](dateReads) and
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
    eventBus.subscribe(self, PoloniexEventBus.NewMarket)
  }

  override def postStop() = {
    stopScheduler()
    eventBus.unsubscribe(self, PoloniexEventBus.NewMarket)
  }

  def receive: Receive = {
    /**
      * Queques a market name.
      */
    case QueueMarket(marketName) =>
      marketQueue.enqueue(marketName)
      startScheduler()

    /**
      * Removes a market name from the queue and retrieves the
      * market candles within the last 24 hours.
      */
    case DequeueMarket =>
      if (marketQueue.nonEmpty) {
        val marketName = marketQueue.dequeue()
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

                // the last 24 hr candles with latest time first
                val last24HrCandles = candles.map( cand =>
                  new MarketCandle(cand.date, periodMinutes, cand.open, cand.close, cand.high, cand.low )
                ).sortBy(_.time).reverse

                //eventBus.publish(MarketEvent(PoloniexEventBus.Candles, Candles(marketName, last24HrCandles)))
                context.parent ! Candles(marketName, last24HrCandles)

                // publish closing prices for this market
                //val closingPrices = MarketCandleClosePrices(marketName, last24HrCandles.map( c => ClosePrice(c.time, c.close)))
                //eventBus.publish(MarketEvent("/market/prices", closingPrices))

              case x =>
                log.error(s"could not retrieve candles for $marketName: ${x.toString}")
            }
          }
        }
      } else {
        // there's noting to retrieve anymore no need to schedule further
        stopScheduler()
      }
  }
}
