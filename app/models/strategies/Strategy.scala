package models.strategies


// internal
import models.analytics.KitchenSink
import models.market.MarketStructures.MarketMessage

trait Strategy {
  def context: KitchenSink

  def handleMessage(msg: MarketMessage)
  def printResults()
  def reset()
}
