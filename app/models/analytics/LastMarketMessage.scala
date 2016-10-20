package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

// internal
import models.market.MarketStructures.MarketMessage

/**
  * Provides DB archiving of messages and candles.
  */
trait LastMarketMessage extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      recordInfo(msg)
      Inner(msg)
  }

  val marketSummaries = scala.collection.mutable.Map[String, MarketMessage]()

  def recordInfo(msg: MarketMessage) = {
    marketSummaries(msg.cryptoCurrency) = msg
  }

  def getLatestMessage(marketName: String) = marketSummaries.get(marketName)
}

