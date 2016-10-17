package services.actors

import java.time.OffsetDateTime

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging}

import scala.collection.mutable.ListBuffer
import models.market.{ClosePrice, MarketExponentialMovingAvgs}
import models.poloniex.MarketMessage2

import scala.math.BigDecimal.RoundingMode


case class BuyRecord(val price: BigDecimal, val quantity: Int, val atVol: BigDecimal)

case class Result(marketName: String, percent: BigDecimal, quantity: Int, atVol: BigDecimal, atCost: BigDecimal, atSale: BigDecimal) {
  override def toString = {
    s"$marketName percent: ${(percent*100).setScale(2, RoundingMode.CEILING)}% quantity: $quantity atVol: $atVol atCost: $atCost atSale: $atSale"
  }
}

trait GoldenCross {
  self: Actor =>

  // marketName -> list of moving averages
  val averages = scala.collection.mutable.Map[String, List[MarketExponentialMovingAvgs]]()

  val marketWatch = scala.collection.mutable.Set[String]()
  val buyRecords = scala.collection.mutable.Map[String, BuyRecord]()

  val maxBTCTradable: BigDecimal = 1.0
  var balance: BigDecimal = 1.0
  var sellCount = 0
  var buyCount = 0
  var winCount = 0
  var lossCount = 0
  var largestWinRecord: Result = Result("", 0, 0, 0, 0, 0)
  var largestLoss: Result = Result("", 0, 0, 0, 0, 0)
  val markets: ListBuffer[String] = ListBuffer[String]()
  val baseVolumeAllowable = 50
  val baseVolumeThreshold = 700
  val gainPercentMin = 0.01
  val lossPercentMin = -0.1

  def setAllMarketAverages(marketAverages: Map[String, List[MarketExponentialMovingAvgs]]) = averages ++= marketAverages

  def handleMessageUpdate: Receive = {
    case msg: MarketMessage2 =>

      val marketName = msg.cryptoCurrency
      val currentPrice = msg.last

      val avgsList = averages(marketName)
      avgsList.foreach(_.updateAverages(ClosePrice(msg.time, currentPrice)))

      val emas = avgsList.map( avgs => (avgs.period, avgs.movingAverages.head.ema)).sortBy(_._1)

      // ema1 shorter period
      val ema1 = emas.head._2
      // ema2 longer period
      val ema2 = emas.last._2

      // if golden cross (ema short greater than ema long)
      // and the market was put on watch
      if (ema1 > ema2 && marketWatch.contains(marketName)) {

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
        val priceDiff = (currentPrice - msg.low24hr) / msg.low24hr

        // can only buy from a market if there isn't an
        // existing share that was purchased
        // we also must have enough in our balance to buy
        // the market 24 base volume must be greater than are minimum base volume allowable so
        // we do not buy from markets that aren't trading at volume
        // finally the price diff from the 24 hr low in this market must be greater than
        // 5 percent (we do not want to buy from markets on their way down)
        if (!buyRecords.contains(marketName) && balance > cost &&
          msg.baseVolume > baseVolumeAllowable && priceDiff > 0.05) {

          balance -= cost
          buyCount += 1
          buyRecords(marketName) = BuyRecord(currentPrice, quantity, msg.baseVolume)
          markets += marketName
          marketWatch -= marketName
        }
      } else if (ema1 < ema2) {

        // watch market if the long ema is greater than ema1
        marketWatch += marketName
      }

      if (buyRecords.contains(marketName)) {
        // sell when the ema1 for this period is less than the previous ema1
        val ema1Curr = ema1
        val ema1Prev = avgsList(0).movingAverages(1).ema
        val buyRecord = buyRecords(marketName)
        //val deltaPrice = currentPrice - buyRecord.price
        val percent = (currentPrice - buyRecord.price) / buyRecord.price

        // if the shorter moving average in the previous period is greater
        // than the current moving average this market is loosing buy momentum
        // the percent in price must also be greater than our min threshold
        if (ema1Prev > ema1Curr && percent > gainPercentMin) {
          if (percent > largestWinRecord.percent) {
            largestWinRecord = Result(marketName, percent, buyRecord.quantity, buyRecord.atVol, buyRecord.price * buyRecord.quantity, currentPrice*buyRecord.quantity)
          }

          winCount += 1
          sellCount += 1
          balance += currentPrice * buyRecord.quantity
          buyRecords.remove(marketName)
        } else if (percent < lossPercentMin) {
          // cut your losses early if the percent of our original buy price is currently
          // below our loss precent threshold

          if (percent < largestLoss.percent) {
            largestLoss = Result(marketName, percent, buyRecord.quantity, buyRecord.atVol, buyRecord.price * buyRecord.quantity, currentPrice*buyRecord.quantity)
          }

          lossCount += 1
          sellCount += 1
          balance += currentPrice * buyRecord.quantity
          buyRecords.remove(marketName)
        }
      }
  }


  def printResults(): Unit = {
    val inventory = buyRecords.foldLeft(BigDecimal(0.0))( (a,r) => (r._2.price * r._2.quantity) + a)
    println(s"Inventory: $inventory")
    println(s"Balance: $balance")
    println(s"Total: ${balance+inventory}")
    println(s"Buy: $buyCount")
    println(s"Sell: $sellCount")
    println(s"Largest Win Record: $largestWinRecord")
    println(s"Largest Loss: $largestLoss")
    println(s"Wins: $winCount")
    println(s"Losses: $lossCount")
  }
}
