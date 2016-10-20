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
    case msg: MarketMessage2 =>
      recordInfo(msg)
      Inner(msg)
  }

  val marketSummaries = scala.collection.mutable.Map[String, MarketMessage2]()

  def recordInfo(msg: MarketMessage2) = {
    marketSummaries(msg.cryptoCurrency) = msg
  }

  def getLatestMessage(marketName: String) = marketSummaries.get(marketName)
}

