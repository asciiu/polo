package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.poloniex.{MarketMessage, MarketMessage2}

// internal
import models.poloniex.MarketUpdate

/**
  * Provides DB archiving of messages and candles.
  */
trait LastMarketMessage extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    // TODO remove this
    case update: MarketUpdate =>
      recordInfo(update)
      Inner(update)

      // TODO you need this
    case msg: MarketMessage2 =>
      Inner(msg)
  }

  val marketSummaries = scala.collection.mutable.Map[String, MarketMessage]()

  def recordInfo(update: MarketUpdate) = {
    marketSummaries(update.marketName) = update.info
  }

  def getLatestMessage(marketName: String) = marketSummaries.get(marketName)
}

