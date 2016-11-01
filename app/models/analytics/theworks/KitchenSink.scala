package models.analytics.theworks

import akka.contrib.pattern.ReceivePipeline
import models.market.MarketStructures.Trade

import scala.collection.mutable.ListBuffer


trait KitchenSink extends ReceivePipeline
  with ExponentialMovingAverages
  with LastMarketMessage
  with MarketCandles
  with OrderFiller
  with Volume24HourTracking  {

  // TODO this should be configurable
  override val periodMinutes = 5

  val buyList = ListBuffer[Trade]()
  val sellList = ListBuffer[Trade]()
}
