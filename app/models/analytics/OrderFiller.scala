package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.db.OrderType

import scala.collection.mutable.ListBuffer

// internal
import models.market.MarketStructures.{MarketMessage}
import models.market.MarketStructures.Order
import models.db

/**
  * Provides DB archiving of messages and candles.
  */
trait OrderFiller extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      processOrders(msg)
      Inner(msg)
  }

  // maps a market name to the current orders on trade
  private val tradeOrders = scala.collection.mutable.Map[String,  ListBuffer[Order]]()
  // place this in a AccountBalance trait
  var balance = BigDecimal(1.0)
  var totalBuys = 0
  var totalSells = 0

  // TODO maybe this belongs elsewhere?
  private val balancesByMarket = scala.collection.mutable.Map[String, BigDecimal]()
  private val btcBalancesByMarket = scala.collection.mutable.Map[String, BigDecimal]()

  // TODO this belongs in another trait
  private val recentFilledOrdersByMarket = scala.collection.mutable.Map[String, Order]()

  def appendOrder(order: Order) = {
    log.info(s"New order: $order")
    val marketName = order.marketName

    tradeOrders.get(marketName) match {
      case Some(orders) =>
        // TODO archive these orders to the DB
        orders += order
      case None =>
        tradeOrders += (order.marketName -> ListBuffer(order))
    }

    if (order.side == OrderType.buy) {
      // subtract from available balance
      balance -= (order.price * order.quantity)
    } else if (order.side == OrderType.sell) {
      // subtract from available market quantity
      balancesByMarket(marketName) = balancesByMarket(marketName) - order.quantity
    }
  }

  def onBuyOrder(marketName: String): Boolean = {
    tradeOrders.get(marketName) match {
      case Some(orders) if orders.nonEmpty =>
        orders.filter(_.side == OrderType.buy).nonEmpty
      case None => false
    }
  }

  def onSellOrder(marketName: String): Boolean = {
    tradeOrders.get(marketName) match {
      case Some(orders) if orders.nonEmpty =>
        orders.filter(_.side == OrderType.sell).nonEmpty
      case None => false
    }
  }

  def onOrder(marketName: String): Boolean = {
    tradeOrders.get(marketName) match {
      case Some(orders) => !orders.isEmpty
      case None => false
    }
  }

  def getOrders(marketName: String): List[Order] = {
    tradeOrders.get(marketName) match {
      case Some(orders) => orders.toList
      case None => List[Order]()
    }
  }

  def getBalance(marketName: String): BigDecimal = {
    balancesByMarket.get(marketName) match {
      case Some(balance) => balance
      case None => 0.0
    }
  }

  def getTotalBalance(): BigDecimal = {
    val inventory = btcBalancesByMarket.map(_._2).sum
    balance + inventory
  }

  def getLastFilledOrder(marketName: String): Option[Order] = {
    recentFilledOrdersByMarket.get(marketName)
  }

  def processOrders(msg: MarketMessage) = {
    val marketName = msg.cryptoCurrency
    tradeOrders.get(marketName) match {
      case Some(orders) =>
        orders.foreach{ o =>
          if(o.side == OrderType.buy && msg.last < o.price) {
            // the last price was less than our buy price
            // this means the order has completely filled
            orders -= o
            recentFilledOrdersByMarket += (marketName -> o)
            balancesByMarket(marketName) = o.quantity + balancesByMarket.getOrElse(marketName, 0)
            totalBuys += 1

          } else if (o.side == OrderType.sell && msg.last > o.price) {
            // the last price was greater than our sell price
            // our sell has completely filled
            orders -= o
            recentFilledOrdersByMarket += (marketName -> o)
            balance += (o.price * o.quantity)
            totalSells += 1
          }
        }
      case None => List[Order]()
    }

    // update the balances
    if (balancesByMarket.contains(marketName)) {
      btcBalancesByMarket(marketName) = balancesByMarket(marketName) * msg.last
    }
  }
}

