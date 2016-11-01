package models.analytics.individual

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketStructures.MarketMessage
import scala.collection.mutable.ListBuffer


trait LastMarketMessage extends ActorLogging {

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
