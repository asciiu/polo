package services.actors

// external
import akka.actor.{Actor, ActorLogging}
import javax.inject.Inject

import models.market.ClosePrice
import play.api.Configuration
import utils.Misc

import scala.language.postfixOps

// internal
import models.poloniex.PoloniexEventBus
import models.poloniex.{MarketEvent, MarketUpdate}

import models.market.MarketCandle
import models.analytics.MarketCandles


object CandleManagerActor {
  trait CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
}

/**
  * This actor is responsible for managing candles for all markets.
  */
class CandleManagerActor @Inject()(conf: Configuration) extends Actor with ActorLogging with MarketCandles {
  import CandleManagerActor._
  import PoloniexCandleRetrieverActor._
  import Misc._

  val eventBus = PoloniexEventBus()
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)

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

        updateMarketCandle(update.marketName, ClosePrice(now(), update.info.last))
      }

    case GetCandles(marketName) =>
      sender ! getMarketCandles(marketName)

    case GetLastestCandle(marketName) =>
      sender ! getLatestCandle(marketName)

    case SetCandles(marketName, last24hrCandles) =>
      appendCandles(marketName, last24hrCandles)
  }
}
