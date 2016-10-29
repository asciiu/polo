package models.strategies


// internal
import models.analytics.KitchenSink
import models.market.MarketStructures.MarketMessage

import scala.math.BigDecimal.RoundingMode

trait Strategy {
  def context: KitchenSink

  def handleMessage(msg: MarketMessage)
  def printResults()
  def reset()

  case class Result(marketName: String, percent: BigDecimal, quantity: BigDecimal, atBuy: BigDecimal, atSale: BigDecimal) {
    override def toString = {
      s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atBuy: $atBuy atSale: $atSale"
    }
  }
}
