package models.analytics

import akka.contrib.pattern.ReceivePipeline
import scala.collection.mutable.ListBuffer
import models.market.MarketStructures.Trade


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
