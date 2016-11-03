package models.analytics.theworks

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.analytics.AccountBalances

import scala.collection.mutable.ListBuffer

// internal
import models.db.OrderType
import models.market.MarketStructures.{MarketMessage, Order}

/**
  * Provides DB archiving of messages and candles.
  */
trait OrderFiller extends ActorLogging with AccountBalances {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      processOrders(msg)
      Inner(msg)
  }

  // maps a market name to the current orders on trade
  private val tradeOrders = scala.collection.mutable.Map[String,  ListBuffer[Order]]()
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
      subtractBTCBalance(order.price * order.quantity)
    } else if (order.side == OrderType.sell) {
      // subtract from available market quantity
      subtractMarketBalance(marketName, order.quantity)
    }
  }

  def onBuyOrder(marketName: String): Boolean = {
    tradeOrders.get(marketName) match {
      case Some(orders) if orders.nonEmpty =>
        orders.filter(_.side == OrderType.buy).nonEmpty
      case None => false
    }
  }

  def cancelOrders() = {
    tradeOrders.clear()
  }

  def onSellOrder(marketName: String): Boolean = {
    tradeOrders.get(marketName) match {
      case Some(orders) if orders.nonEmpty =>
        orders.filter(_.side == OrderType.sell).nonEmpty
      case None => false
    }
  }

  def openBuyOrders(): List[Order] = {
    tradeOrders.values.flatten.filter(_.side == OrderType.buy).toList
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

            addMarketBalance(marketName, o.quantity)
            o.callback(o, msg.time)

            //totalBuys += 1
            //buyList += Trade(marketName, msg.time, o.price, o.quantity)

          } else if (o.side == OrderType.sell && msg.last > o.price) {
            // the last price was greater than our sell price
            // our sell has completely filled
            orders -= o
            recentFilledOrdersByMarket += (marketName -> o)
            addBTCBalance(o.price * o.quantity)
            o.callback(o, msg.time)
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

