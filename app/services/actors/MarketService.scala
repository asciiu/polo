package services.actors

// external
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import java.time.OffsetDateTime

import models.analytics.individual.KitchenSink
import models.poloniex.{MarketEvent, PoloniexEventBus}
import models.strategies.BollingerAlertStrategy
import play.api.libs.json.{JsArray, Json}

import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

// internal
import models.analytics.Archiving
import models.market.MarketStructures._
import services.DBService

object MarketService {
  def props(marketName: String, database: DBService)(implicit context: ExecutionContext) =
    Props(new MarketService(marketName, database))

  case class SendCandles(out: ActorRef)
  case class SendLatestCandle(out: ActorRef)
  case class SendLatestMovingAverages(out: ActorRef)
  case class SendMovingAverages(out: ActorRef)
  case class SendVolume(out: ActorRef, time: OffsetDateTime)
  case class SendVolumes(out: ActorRef)
  case class SendLatestMessage(out: ActorRef)
  case class SendBollingerBands(out: ActorRef)
  case class SendLatestBollingerBands(out: ActorRef)

  case class Update(message: MarketMessage, candleData: JsArray)
}

class MarketService(val marketName: String, val database: DBService) extends Actor
  with ActorLogging
  with ReceivePipeline
  with KitchenSink
  with Archiving {

  import MarketService._

  val eventBus = PoloniexEventBus()
  val strategy = new BollingerAlertStrategy(this)

  override def preStart() = {
    log.info(s"Started $marketName service")
  }

  private def publishUpdate(msg: MarketMessage) = {
    val averages = getLatestMovingAverages()

    getLatestCandle() match {
      case Some(candle) if (averages.nonEmpty) =>
        val volume24Hr = getVolume(candle.time)
        val bollingers = getLatestPoints()
        val bands = bollingers.getOrElse(BollingerBandPoint(candle.time, 0, 0, 0))
        val candleData = Json.arr(
          // TODO UTF offerset should come from client
          candle.time.toEpochSecond() * 1000L - 2.16e+7,
          candle.open,
          candle.high,
          candle.low,
          candle.close,
          averages.head._2,
          averages.last._2,
          volume24Hr.btcVolume.setScale(2, RoundingMode.DOWN),
          bands.center,
          bands.upper,
          bands.lower
        )
        val update = Update(msg, candleData)
        eventBus.publish(MarketEvent(PoloniexEventBus.Updates + s"/$marketName", update))

      case _ =>
    }
  }

  override def postStop() = {
    log.info(s"Shutdown $marketName service")
  }

  def receive: Receive = {
    case msg: MarketMessage =>
      strategy.handleMessage(msg)
      publishUpdate(msg)

    case SendCandles(out) =>
      out ! getCandles()

    case SendLatestCandle(out) =>
      out ! getLatestCandle()

    case SendLatestMovingAverages(out) =>
      out ! getLatestMovingAverages()

    case SendMovingAverages(out) =>
      out ! getMovingAverages()

    case SendVolume(out, time) =>
      out ! getVolume(time)

    case SendVolumes(out) =>
      out ! getVolumes()

    case SendLatestMessage(out) =>
      out ! getLatestMessage()

    case SendBollingerBands(out) =>
      out ! getAllPoints()

    case SendLatestBollingerBands(out) =>
      out ! getLatestPoints()
  }
}

