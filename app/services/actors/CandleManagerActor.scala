package services.actors

// external

import akka.actor.{Actor, ActorLogging}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import java.time.OffsetDateTime
import javax.inject.Inject

import models.analytics.Volume24HourTracking
import play.api.Configuration

import scala.language.postfixOps

// internal
import utils.Misc
import models.poloniex.PoloniexEventBus
import models.poloniex.{MarketEvent, MarketUpdate}
import models.market.MarketCandle
import models.analytics.MarketCandles
import models.analytics.Archiving
import models.analytics.ExponentialMovingAverages
import services.DBService


object CandleManagerActor {
  trait CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
  case object StartCapture extends CandleManagerMessage
  case object EndCapture extends CandleManagerMessage
  case class GetMovingAverage(marketname: String, time: OffsetDateTime) extends CandleManagerMessage
  case class GetMovingAverages(marketName: String) extends CandleManagerMessage
  case class GetVolume(marketName: String, time: OffsetDateTime) extends CandleManagerMessage
  case class GetVolumes(marketName: String) extends CandleManagerMessage
}

/**
  * This actor is responsible for managing candles for all markets.
  */
class CandleManagerActor @Inject()(val database: DBService,
                                   conf: Configuration) extends Actor
  with ActorLogging
  with ReceivePipeline
  with MarketCandles
  with ExponentialMovingAverages
  with Volume24HourTracking
  with Archiving {

  import CandleManagerActor._
  import PoloniexCandleRetrieverActor._

  // This must execute before the interceptors in the other
  // traits
  pipelineOuter {
    // need to catch the update messages first so
    // we can signal if we need to retrieve the candles
    case update: MarketUpdate =>
      val marketName = update.marketName

      // only care about BTC markets
      if (marketName.startsWith("BTC")) {

        if (!marketCandles.contains(marketName)) {
          // send a message to the retriever to get the candle data from Poloniex
          // if the 24 hour baseVolume from this update is greater than our threshold
          if (update.info.baseVolume > baseVolumeRule) {
            eventBus.publish(MarketEvent("/market/added", QueueMarket(marketName)))
          }
        }
      }
      Inner(update)
  }

  val eventBus = PoloniexEventBus()
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)
  override val periodMinutes = 5

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
    eventBus.subscribe(self, "/market/candles")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/update")
    eventBus.unsubscribe(self, "/market/candles")
  }

  def receive: Receive = {

    case StartCapture =>
      // TODO fix you also need to save the current candles to the DB
      beginSession()
    case EndCapture =>
      endSession()

    case GetCandles(marketName) =>
      sender ! getMarketCandles(marketName)

    case GetLastestCandle(marketName) =>
      sender ! getLatestCandle(marketName)

    /**
      * Returns a List[(Int, BigDecimal)] to the sender
      */
    case GetMovingAverage(marketName, time) =>
      sender ! getMovingAverage(marketName, time)

    /**
      * Send to original sender the moving averages of a market.
      * retunrs a List[(Int, List[ExponentialMovingAverage])]
      */
    case GetMovingAverages(marketName) =>
      sender ! getMovingAverages(marketName)

    case GetVolume(marketName, time) =>
      sender ! getVolume(marketName, time)

    case GetVolumes(marketName) =>
      sender ! getVolumes(marketName)
  }
}
