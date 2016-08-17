package utils.poloniex

import akka.event.{ActorEventBus, LookupClassification}
import models.poloniex.{Market, MarketCandle}


case class MarketEvent(topic: String, market: Market)

/**
  * Created by bishop on 8/16/16.
  */
class PoloniexEventBus extends ActorEventBus with LookupClassification {
  type Event = MarketEvent
  type Classifier = String

  protected def mapSize(): Int = 10

  protected def classify(event: Event): Classifier = {
    event.topic
  }

  protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event.market
  }
}

object PoloniexEventBus {
  lazy val instance = new PoloniexEventBus
  def apply() = instance
}