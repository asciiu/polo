package models.strategies

import java.time.OffsetDateTime

import models.market.MarketCandle
import models.neuron.NeuralNet
import utils.Misc

import scala.collection.mutable.ListBuffer

// internal
import models.analytics.individual.KitchenSink
import models.db.OrderType
import models.market.MarketStructures.{MarketMessage, Order, Trade}


class GoldenCrossStrategy(val context: KitchenSink) extends Strategy {

  case class Watchamacallit(time: OffsetDateTime, price: BigDecimal, inputs: Array[BigDecimal], var targetPercent: BigDecimal = 0)
  val thingy = scala.collection.mutable.Map[String, ListBuffer[Watchamacallit]]()


  val marketWatch = scala.collection.mutable.Set[String]()
  val maxBTCTradable: BigDecimal = 1.0
  val baseVolumeAllowable = 50
  val baseVolumeThreshold = 700
  val gainPercentMin = 0.015
  val lossPercentMin = -0.03
  var totalBuys = 0
  var totalSells = 0
  var winCount = 0
  var lossCount = 0
  var largestWinRecord: Result = Result("", 0, 0, 0, 0)
  var largestLoss: Result = Result("", 0, 0, 0, 0)
  val winningMarkets = ListBuffer[String]()
  val loosingMarkets = ListBuffer[String]()
  val markets: ListBuffer[String] = ListBuffer[String]()

  val neuralNet = new NeuralNet(Array(3, 4, 2))

  def reset() = {
    totalBuys = 0
    totalSells = 0
    winCount = 0
    lossCount = 0
    largestWinRecord = Result("", 0, 0, 0, 0)
    largestLoss = Result("", 0, 0, 0, 0)
    markets.clear()
    context.buyList.clear()
    context.sellList.clear()
    context.setAvailableBalance(1.0)
  }

  def totalBalance: BigDecimal = {
    val openOrders = context.openBuyOrders()
    openOrders.foldLeft( context.getTotalBalance() )( (a, o) => a + (o.price*o.quantity))
  }

  def printResults(): Unit = {
    // get all highs in candles
    //println(s"Inventory: $inventoryBalance")
    println(s"Balance: ${context.availableBalance}")
    println(s"Total: $totalBalance")
    println(s"Buy: $totalBuys")
    println(s"Sell: $totalSells")
    println(s"Wins: $winCount")
    println(s"Losses: $lossCount")
    println(s"Largest Win: $largestWinRecord")
    println(s"Largest Loss: $largestLoss")
    println(s"Winning Markets: \n$winningMarkets")
    println(s"Loosing Markets: \n$loosingMarkets")
  }

  val lastCrossTime = scala.collection.mutable.Map[String, OffsetDateTime]()

  def handleMessage(msg: MarketMessage) = {
    val marketName = msg.cryptoCurrency
    markets += marketName
    val emas = context.getLatestMovingAverages().map( _._2 )
    val currentCandle = context.getLatestCandle()

    // we must have averages in order to trade
    if (emas.nonEmpty && currentCandle.nonEmpty) {
      val ema1 = emas.head
      val ema2 = emas.last
      val candle = currentCandle.get

      // these values need to be tracked
      // so we can fill in the target values for this moment
      // at a later time
      val in1 = msg.last
      val in2 = msg.time

      // inputs
      val i1 = ema1 - ema2
      val i2 = (msg.last - msg.low24hr) / msg.low24hr
      val i3 = (msg.last - msg.high24hr) / msg.high24hr
      // candle low
      val i4 = (msg.last - candle.low) / candle.low
      // 24 hour percent change
      val i5 = msg.percentChange

      val inputs = Array(i1, i2, i3, i4, i5)

      val thing = Watchamacallit(in2, in1, inputs)

      // neural network should determine
      // going up by 1% yes or no within 10 - 15 minutes

//        thingy.get(marketName) match {
//          case Some(buffer) =>
//            buffer.append(thing)
//
//            // next we need to update the thing from 5 minutes ago
//            val time = msg.time.minusMinutes(15)
//            buffer.filter( t => t.time.isBefore(time) && t.targetPercent == 0).foreach{ t =>
//              val percent = msg.last - t.price / t.price
//              t.targetPercent = percent
//              buffer -= t
//            }
//
//          case None =>
//            thingy += marketName -> ListBuffer(thing)
//        }


        // TODO add the message count in this period


        // buy or sell

        // buy or sell
        // max percent gain per day

        // what price to trade at

    }
  }

  def tryBuy(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal) = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    //val avgsList = context.allAverages(marketName)
    val avgsList = context.getMovingAverages()
    // ema1 shorter period
    val ema1Prev = avgsList.head._2(1).ema
    // there should be a candle
    val candle = context.getLatestCandle()

    // TODO perhaps read the order book to determine buy price?
    // for now divide the candle height by 4 and add the
    // delta to the candle low
    val buyPrice = (currentPrice - ema2) / 2 + ema2
    //val buyPrice = currentPrice

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
    val bs5 = context.availableBalance > cost
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
    val buyPrice = context.buyList.filter(_.marketName == order.marketName).last.price
    val percent = (order.price - buyPrice) / buyPrice

    if (percent > 0)  {
      winCount += 1

      if (largestWinRecord.percent < percent){
        val quantity = order.quantity
        largestWinRecord = Result(order.marketName, percent, order.quantity, buyPrice * quantity, order.price * quantity)
      }
    }

    if (percent < 0) {
      lossCount += 1

      if (largestLoss.percent > percent) {
        val quantity = order.quantity
        largestLoss = Result(order.marketName, percent, order.quantity, buyPrice * quantity, order.price * quantity)
      }
    }
  }

  def trySell(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal): Unit = {
    val marketName = msg.cryptoCurrency
    val currentPrice = msg.last
    val avgsList = context.getMovingAverages()
    val quantity = context.getMarketBalance(marketName)

    if (quantity > 0) {
      // sell when the ema1 for this period is less than the previous ema1
      val ema1Prev = avgsList.head._2(1).ema
      val buyOrder = context.buyList.filter(_.marketName == marketName).last
      val buyPrice = buyOrder.price
      val percent = (currentPrice - buyPrice) / buyPrice

      // sell signals
      val sc1 = ema1Prev > ema1
      val sc2 = percent > gainPercentMin
      val sc3 = (ema1 - ema2) / ema2 < 0.005
      val elapsedMinutes = (msg.time.toEpochSecond - buyOrder.time.toEpochSecond) / 60

      // if the shorter moving average in the previous period is greater
      // than the current moving average this market is loosing buy momentum
      // the percent in price must also be greater than our min threshold
      if (sc2) {
        // create a sell order
        context.appendOrder(Order(msg.time, marketName, currentPrice, quantity, OrderType.sell, incrementSellFill))

      } else if (percent < 0 && elapsedMinutes > 300) {
        // cut your losses early if the percent of our original buy price is currently
        // below our loss precent threshold
        context.appendOrder(Order(msg.time, marketName, currentPrice, quantity, OrderType.sell, incrementSellFill))
      }
    }
  }
}
