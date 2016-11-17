package models.strategies

import java.time.OffsetDateTime

import models.analytics.individual.KitchenSink
import models.db.OrderType
import models.market.MarketCandle
import models.market.MarketStructures.{BollingerBandPoint, MarketMessage, Order, Trade}
import utils.Misc

import scala.collection.mutable.ListBuffer


/**
  * Auto trade strategy using bollinger bands.
  *
  * @param context
  */
class BollingerStrategy(val context: KitchenSink) extends Strategy {
  case class BuyRecord(val price: BigDecimal, val quantity: Int, val atVol: BigDecimal)

  val buyRecords = ListBuffer[BuyRecord]()
  var buyCount = 0
  var sellCount = 0
  var isReady = false
  var balance: BigDecimal = 1.0
  var resistance: BigDecimal = 0.0

  def inventoryBalance: BigDecimal = {
    1.0
  }

  def totalBalance: BigDecimal = {
    1.0
  }

  def handleMessage(msg: MarketMessage) = {

    val bollinger = context.getLatestPoints().getOrElse(BollingerBandPoint(msg.time, 0, 0, 0))
    val currentPrice = msg.last
    val normalTime = Misc.roundDateToMinute(msg.time, 5)
    val marketName = msg.cryptoCurrency

    if (currentPrice > resistance) resistance = currentPrice

    // wait for the current price to go below the bollinger average
    if (currentPrice < bollinger.lower && !isReady) {
      isReady = true
    } else if (isReady && currentPrice > bollinger.upper && isFiveMinFromLastBuy(msg.time) && !context.onBuyOrder(marketName)) {

      // is there an increase in volume?
      val previous24HrVolume = context.getVolume(normalTime.minusMinutes(5)).btcVolume
      val current24HrVolume = context.getVolume(normalTime).btcVolume
      val percentChange = (current24HrVolume - previous24HrVolume) / previous24HrVolume

      // if the volume change is greater than 2 percent
      if (current24HrVolume < 100 && percentChange > 0.02) {
        val quantity = balance / resistance
        // open a buy order
        context.appendOrder(Order(msg.time, msg.cryptoCurrency, resistance, quantity, OrderType.buy, buyFilled))
      } else if (current24HrVolume > 200) {
        val quantity = balance / resistance
        // open a buy order
        context.appendOrder(Order(msg.time, msg.cryptoCurrency, resistance, quantity, OrderType.buy, buyFilled))
      }
    }

    val marketbalance = context.getMarketBalance(marketName)
    val thisCandle = context.getLatestCandle()
    val lastMinuteOfCandle = (msg.time.getMinute() % 5) > 3

    if (marketbalance > 0) {
      val buyPrice = context.buyList.last.rate

      // stop loss
      if (isStopLoss(buyPrice, currentPrice, -0.03) || (thisCandle.isDefined && !thisCandle.get.isBuy && lastMinuteOfCandle)) {
        context.appendOrder(Order(msg.time, marketName, currentPrice, marketbalance, OrderType.sell, sellFilled))
      }
    }
  }

  def buyFilled(order: Order, time: OffsetDateTime) = {
    //context.buyList += Trade(order.marketName, time, order.price, order.quantity)
    balance -= order.price * order.quantity
    buyCount += 1
  }

  def sellFilled(order: Order, time: OffsetDateTime) = {
    //context.sellList += Trade(order.marketName, time, order.price, order.quantity)
    sellCount += 1
    balance += order.price * order.quantity
  }

  /**
    * Determines if the current price has reached a stop loss percent.
    */
  private def isStopLoss(buyPrice: BigDecimal, currentPrice: BigDecimal, atPercent: BigDecimal): Boolean = {
    val deltaPercent = (currentPrice - buyPrice) / buyPrice
    if (deltaPercent < atPercent) true
    else false
  }

  private def isFiveMinFromLastBuy(time: OffsetDateTime): Boolean = {
    context.buyList.lastOption match {
      case Some(buy) =>
        val buyTime = buy.time.toEpochSecond()
        val thisTime = time.toEpochSecond()

        // has five minute elapsed?
        (thisTime - buyTime) / 300 > 5
      case None => true
    }
  }

  def train() = {}

  def reset(): Unit = {
    context.buyList.clear()
    context.sellList.clear()
    context.cancelOrders()
    balance = 1.0
    buyCount = 0
    sellCount = 0
    resistance = 0
  }

  def printResults(): Unit = {
    println(s"Balance: $balance")
    println(s"Buys: $buyCount")
    println(s"Sells: $sellCount")
  }
}
