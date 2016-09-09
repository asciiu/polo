package models.poloniex

/**
  * Created by bishop on 9/8/16.
  */
case class Order(orderNumber: Long, side: String, rate: BigDecimal, startingAmount: BigDecimal, amount: BigDecimal, total: BigDecimal) {
  type Long
}

case class OrdersOpened(marketName: String, orders: List[Order])

case class MoveOrderStatus(status: Int, orderNumber: Long)

