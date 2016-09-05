package utils.poloniex

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}
import models.poloniex.{MarketCandle, PoloMarketCandle}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import utils.poloniex.PoloniexCandleCreatorActor.SetCandles

import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by bishop on 9/4/16.
  */
object PoloniexCandleRetrieverActor {
  def props(ws: WSClient)(implicit system: ActorSystem): Props = Props(new PoloniexCandleRetrieverActor(ws))

  trait CandleRetrieverMessage
  case class QueueMarket(marketName: String) extends CandleRetrieverMessage
  case object DequeueMarket extends CandleRetrieverMessage
}

class PoloniexCandleRetrieverActor(ws: WSClient)(implicit system: ActorSystem) extends Actor with ActorLogging {
  import PoloniexCandleRetrieverActor._
  import scala.concurrent.duration._

  val marketQueue = scala.collection.mutable.Queue[String]()
  var schedule: Option[Cancellable] = None
  object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  }
  import Joda._

  private def startScheduler() = {
    if (!schedule.isDefined) {
      log.info("scheduler started")
      schedule = Some(system.scheduler.schedule(1 seconds, 3 seconds, self, DequeueMarket))
    }
  }
  private def stopScheduler() = {
    if (schedule.isDefined) {
      log.info("scheduler stopped")
      schedule.get.cancel()
    }
  }

  override def preStart() = {}
  override def postStop() = {
    stopScheduler()
  }


  //[{"date":1405699200,"high":0.0045388,"low":0.00403001,"open":0.00404545,"close":0.00427592,"volume":44.11655644,
  //"quoteVolume":10259.29079097,"weightedAverage":0.00430015}, ...]
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

  //val jodaDateReads = Reads[DateTime](js =>
  //  js.validate[Long].map[DateTime](dtSeconds =>
  //    new DateTime((dtSeconds * 1000L))
  //  )
  //)

  implicit val marketStatus = Json.reads[PoloMarketCandle]

  def receive: Receive = {
    case QueueMarket(marketName) =>
      marketQueue.enqueue(marketName)
      startScheduler()
    case DequeueMarket =>
      if (marketQueue.nonEmpty) {
        val marketName = marketQueue.dequeue()
        // TODO remove this condition
        if (marketName == "BTC_XMR") {
          // timeStart is 24 hours from now
          val nowUTC = new DateTime(DateTimeZone.UTC)
          val timeStartSeconds = nowUTC.getMillis() / 1000L - 86400L
          val periodLength = 300
          val url = s"https://poloniex.com/public?command=returnChartData&currencyPair=$marketName&start=$timeStartSeconds&end=9999999999&period=$periodLength"
          val poloniexRequest: WSRequest = ws.url(url)
            .withHeaders("Accept" -> "application/json")
            .withRequestTimeout(10000.millis)

          poloniexRequest.get().map { polo =>
            polo.json.validate[List[PoloMarketCandle]] match {
              case JsSuccess(candles, t) =>
                val last24HrCandles = candles.map { pc => MarketCandle(pc) }.sortBy(_.time).reverse
                context.parent ! SetCandles(marketName, last24HrCandles)
              case x =>
                log.warning(s"could not retrieve candles for $marketName: ${x.toString}")
            }
          }
        }
      } else {
        stopScheduler()
      }
  }
}
