package models.strategies


// internal
import models.analytics._
import models.market.MarketStructures.MarketMessage
import scala.math.BigDecimal.RoundingMode


/**
  * Newer strategy designed for single market actors.
  */
trait Strategy {
  def context: individual.KitchenSink

  def handleMessage(msg: MarketMessage)
  def printResults()
  def reset()

  case class Result(marketName: String, percent: BigDecimal, quantity: BigDecimal, atBuy: BigDecimal, atSale: BigDecimal) {
    override def toString = {
      s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atBuy: $atBuy atSale: $atSale"
    }
  }
}


/**
  * Older strategy. May be deprecated in the future.
  */
trait GrandfatherStrategy {
  def context: theworks.KitchenSink

  def handleMessage(msg: MarketMessage)
  def printResults()
  def reset()

  case class Result(marketName: String, percent: BigDecimal, quantity: BigDecimal, atBuy: BigDecimal, atSale: BigDecimal) {
    override def toString = {
      s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atBuy: $atBuy atSale: $atSale"
    }
  }
}
