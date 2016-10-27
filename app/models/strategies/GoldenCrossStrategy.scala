package models.strategies

import java.time.OffsetDateTime

import akka.actor.Actor
import akka.contrib.pattern.ReceivePipeline
import utils.Misc
import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

// internal
import models.analytics.{ExponentialMovingAverages, OrderFiller}
import models.market.MarketStructures.{MarketMessage, Order}
import models.db.OrderType


trait GoldenCrossStrategy extends ReceivePipeline
  with ExponentialMovingAverages
  with OrderFiller {

  self: Actor =>

  case class BuyRecord(val price: BigDecimal, val quantity: Int, val atVol: BigDecimal)

  case class Result(marketName: String, percent: BigDecimal, quantity: Int, atBuy: BigDecimal, atSale: BigDecimal) {
    override def toString = {
      s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atBuy: $atBuy atSale: $atSale"
    }
  }

  case class Trade(marketName: String, val time: OffsetDateTime, val price: BigDecimal, val quantity: BigDecimal)

  val buyList = scala.collection.mutable.ListBuffer[Trade]()
  val sellList = scala.collection.mutable.ListBuffer[Trade]()
  // marketName -> list of moving averages
  //val averages = scala.collection.mutable.Map[String, List[MarketEMACollection]]()

  val marketWatch = scala.collection.mutable.Set[String]()
  val buyRecords = scala.collection.mutable.Map[String, BuyRecord]()

  var maxBTCTradable: BigDecimal = 1.0
  var total: BigDecimal = balance
  var sellCount = 0
  var buyCount = 0
  var winCount = 0
  var lossCount = 0
  var largestWinRecord: Result = Result("", 0, 0, 0, 0)
  var largestLoss: Result = Result("", 0, 0, 0, 0)
  val winningMarkets = ListBuffer[String]()
  val loosingMarkets = ListBuffer[String]()
  val markets: ListBuffer[String] = ListBuffer[String]()
  val baseVolumeAllowable = 50
  val baseVolumeThreshold = 700
  val gainPercentMin = 0.01
  val lossPercentMin = -0.1

  //def setAllMarketAverages(marketAverages: Map[String, List[MarketEMACollection]]) = averages ++= marketAverages
  def reset() = {
    balance = 1.0
    total = 1.0
    sellCount = 0
    buyCount = 0
    winCount = 0
    lossCount = 0
    largestWinRecord = Result("", 0, 0, 0, 0)
    largestLoss = Result("", 0, 0, 0, 0)
    markets.clear()
    buyList.clear()
    sellList.clear()
    marketWatch.clear()
    buyRecords.clear()
  }

  def totalBalance: BigDecimal = getTotalBalance

  def handleMessageUpdate: Receive = {
    case msg: MarketMessage =>
      val marketName = msg.cryptoCurrency
      val emas = getLatestMovingAverages(marketName).sortBy(_._1).map( _._2 )

      // we must have averages in order to trade
      if (emas.nonEmpty) {
        val ema1 = emas.head
        val ema2 = emas.last

        tryBuy(msg, ema1, ema2)
        trySell(msg, ema1, ema2)

        // forcast markets that satisfy these conditions
        val fc1 = ema1 < ema2
        val fc2 = !onOrder(marketName)
        val fc3 = msg.baseVolume > baseVolumeAllowable
        val fc4 = getBalance(marketName) == 0
        if (fc1 && fc2 && fc3 && fc4) {
          // begin monitoring this market for entry
          marketWatch += marketName
        }
      }
  }

  def tryBuy(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal) = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    val avgsList = averages(marketName)
    // ema1 shorter period
    val ema1Prev = avgsList(0).movingAverages(1).ema

    // if the current 24 hour base volume of this market is greater
    // than the threshold we buy less because these markets
    // most likely have already spiked from trading activity
    // we want to assume less risk in these markets so we buy less
    val quantity =
    // 2 percent of tradable BTC shall be purchased
      if (msg.baseVolume > baseVolumeThreshold) (0.02 * maxBTCTradable / currentPrice).toInt
      // 7 percent for markets that are less volatile
      else (0.07 * maxBTCTradable / currentPrice).toInt

    val cost = currentPrice * quantity

    // buy signals
    val bs1 = ema1 > ema2
    val bs2 = marketWatch.contains(marketName)
    val bs3 = ema1 > ema1Prev
    val bs4 = quantity > 0
    val bs5 = balance > cost
    val conditions = List(bs1, bs2, bs3, bs4, bs5)

    // if all buy conditions are true
    if (conditions.reduce( (c1, c2) => c1 && c2)) {
      marketWatch -= marketName
      appendOrder(Order(Misc.now(), marketName, currentPrice, quantity, OrderType.buy))
      buyList += Trade(marketName, Misc.now(), currentPrice, quantity)
    }
  }

  def trySell(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal): Unit = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    val avgsList = averages(marketName)
    val quantity = getBalance(marketName)
    val filledOrder = getLastFilledOrder(marketName)

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
      if (sc1 && sc2 && sc3) {
        // create a sell order
        appendOrder(Order(Misc.now(), marketName, currentPrice, quantity, OrderType.sell))
        sellList += Trade(marketName, Misc.now(), currentPrice, quantity)

//        if (largestWinRecord.percent < percent) {
//          largestWinRecord = Result(marketName, percent, quantity, buyPrice*quantity, currentPrice)
//        }
      } else if (percent < lossPercentMin) {
        // cut your losses early if the percent of our original buy price is currently
        // below our loss precent threshold
        appendOrder(Order(Misc.now(), marketName, currentPrice, quantity, OrderType.sell))
        sellList += Trade(marketName, Misc.now(), currentPrice, quantity)
      }
    }
  }

  def printResults(): Unit = {
    //println(s"Inventory: $inventoryBalance")
    println(s"Balance: $balance")
    println(s"Total: ${getTotalBalance()}")
    println(s"Buy: $totalBuys")
    println(s"Sell: $totalSells")
    println(s"Wins: $winCount")
    println(s"Losses: $lossCount")
    println(s"Largest Win: $largestWinRecord")
    println(s"Largest Loss: $largestLoss")
    println(s"Winning Markets: \n$winningMarkets")
    println(s"Loosing Markets: \n$loosingMarkets")
  }
}
