package models.strategies

import java.time.OffsetDateTime
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

// internal
import models.analytics.KitchenSink
import models.db.OrderType
import models.market.MarketStructures.{MarketMessage, Order, Trade}


class GoldenCrossStrategy(val context: KitchenSink) extends Strategy {

  val marketWatch = scala.collection.mutable.Set[String]()
  val maxBTCTradable: BigDecimal = 1.0
  val baseVolumeAllowable = 50
  val baseVolumeThreshold = 700
  val gainPercentMin = 0.015
  val lossPercentMin = -0.1

  def totalBalance: BigDecimal = context.getTotalBalance

  case class Result(marketName: String, percent: BigDecimal, quantity: BigDecimal, atBuy: BigDecimal, atSale: BigDecimal) {
    override def toString = {
      s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atBuy: $atBuy atSale: $atSale"
    }
  }

  var totalBuys = 0
  var totalSells = 0
  var winCount = 0
  var lossCount = 0
  var largestWinRecord: Result = Result("", 0, 0, 0, 0)
  var largestLoss: Result = Result("", 0, 0, 0, 0)
  val winningMarkets = ListBuffer[String]()
  val loosingMarkets = ListBuffer[String]()
  val markets: ListBuffer[String] = ListBuffer[String]()

  def reset() = {
    winCount = 0
    lossCount = 0
    largestWinRecord = Result("", 0, 0, 0, 0)
    largestLoss = Result("", 0, 0, 0, 0)
    markets.clear()
  }

  def printResults(): Unit = {
    //println(s"Inventory: $inventoryBalance")
    println(s"Balance: ${context.balance}")
    println(s"Total: ${context.getTotalBalance()}")
    println(s"Buy: $totalBuys")
    println(s"Sell: $totalSells")
    println(s"Wins: $winCount")
    println(s"Losses: $lossCount")
    println(s"Largest Win: $largestWinRecord")
    println(s"Largest Loss: $largestLoss")
    println(s"Winning Markets: \n$winningMarkets")
    println(s"Loosing Markets: \n$loosingMarkets")
  }

  def handleMessage(msg: MarketMessage) = {
      val marketName = msg.cryptoCurrency
      val emas = context.getLatestMovingAverages(marketName).sortBy(_._1).map( _._2 )

      // we must have averages in order to trade
      if (emas.nonEmpty) {
        val ema1 = emas.head
        val ema2 = emas.last

        tryBuy(msg, ema1, ema2)
        trySell(msg, ema1, ema2)

        // forecast markets that satisfy these conditions
        val fc1 = ema1 < ema2
        val fc2 = !context.onOrder(marketName)
        val fc3 = msg.baseVolume > baseVolumeAllowable
        val fc4 = context.getMarketBalance(marketName) == 0
        if (fc1 && fc2 && fc3 && fc4) {
          // begin monitoring this market for entry
          marketWatch += marketName
        }
      }
  }

  def tryBuy(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal) = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    val avgsList = context.averages(marketName)
    // ema1 shorter period
    val ema1Prev = avgsList(0).movingAverages(1).ema
    // there should be a candle
    val candle = context.getLatestCandle(marketName)

    // TODO perhaps read the order book to determine buy price?
    // for now divide the candle height by 4 and add the
    // delta to the candle low
    val buyPrice = candle match {
      case Some(c) =>
        val half = (c.high - c.low) / 4
        c.low + half
      case None =>
        currentPrice
    }

    // if the current 24 hour base volume of this market is greater
    // than the threshold we buy less because these markets
    // most likely have already spiked from trading activity
    // we want to assume less risk in these markets so we buy less
    val quantity =
    // 2 percent of tradable BTC shall be purchased
      if (msg.baseVolume > baseVolumeThreshold) (0.02 * maxBTCTradable / buyPrice).toInt
      // 7 percent for markets that are less volatile
      else (0.07 * maxBTCTradable / buyPrice).toInt

    val cost = buyPrice * quantity

    // buy signals
    val bs1 = ema1 > ema2
    val bs2 = marketWatch.contains(marketName)
    val bs3 = ema1 > ema1Prev
    val bs4 = quantity > 0
    val bs5 = context.balance > cost
    val conditions = List(bs1, bs2, bs3, bs4, bs5)

    // if all buy conditions are true
    if (conditions.reduce( (c1, c2) => c1 && c2)) {
      marketWatch -= marketName
      context.appendOrder(Order(msg.time, marketName, buyPrice, quantity, OrderType.buy, incrementBuyFill))
      //buyList += Trade(marketName, msg.time, currentPrice, quantity)
    }
  }

  def incrementBuyFill(order: Order, fillTime: OffsetDateTime): Unit = {
    totalBuys += 1
    context.buyList += Trade(order.marketName, fillTime, order.price, order.quantity)
  }

  def incrementSellFill(order: Order, fillTime: OffsetDateTime): Unit = {
    totalSells += 1
    context.sellList += Trade(order.marketName, fillTime, order.price, order.quantity)
  }

  def trySell(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal): Unit = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    val avgsList = context.averages(marketName)
    val quantity = context.getMarketBalance(marketName)
    val filledOrder = context.getLastFilledOrder(marketName)

    if (quantity > 0 && filledOrder.nonEmpty && filledOrder.get.side == OrderType.buy) {
      // sell when the ema1 for this period is less than the previous ema1
      val ema1Prev = avgsList(0).movingAverages(1).ema
      val buyPrice = filledOrder.get.price
      val percent = (currentPrice - buyPrice) / buyPrice

      // sell signals
      val sc1 = ema1Prev > ema1
      val sc2 = percent > gainPercentMin
      val sc3 = (ema1 - ema2) / ema2 < 0.005

      // if the shorter moving average in the previous period is greater
      // than the current moving average this market is loosing buy momentum
      // the percent in price must also be greater than our min threshold
      if (sc1) {
        // create a sell order
        context.appendOrder(Order(msg.time, marketName, currentPrice, quantity, OrderType.sell, incrementSellFill))
        //sellList += Trade(marketName, msg.time, currentPrice, quantity)
        //winCount += 1

        //if (largestWinRecord.percent < percent) {
        //  largestWinRecord = Result(marketName, percent, quantity, buyPrice*quantity, currentPrice*quantity)
        //}
      } else if (percent < lossPercentMin) {
        // cut your losses early if the percent of our original buy price is currently
        // below our loss precent threshold
        context.appendOrder(Order(msg.time, marketName, currentPrice, quantity, OrderType.sell, incrementSellFill))
        //sellList += Trade(marketName, msg.time, currentPrice, quantity)
        //lossCount += 1
        //if (largestLoss.percent > percent) {
        //  largestLoss = Result(marketName, percent, quantity, buyPrice*quantity, currentPrice*quantity)
        //}
      }
    }
  }
}
