package models.analytics.theworks

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketStructures.MarketMessage

/**
  * Created by bishop on 11/1/16.
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
