package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

import scala.collection.mutable.ListBuffer

// internal
import models.market.MarketStructures.MarketMessage

trait LastMarketMessage2 extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      if (lastMessage.nonEmpty) lastMessage.update(0, msg)
      else lastMessage.append(msg)
      Inner(msg)
  }


  // TODO this trait should keep track of a limited
  // sequence of recent messages
  val lastMessage = ListBuffer[MarketMessage]()

  def getLatestMessage() = lastMessage.headOption
}


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
