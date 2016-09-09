package models.poloniex

import org.joda.time.DateTime

/**
  * Created by bishop on 9/8/16.
  */
case class Trade(date: DateTime,
                 amount: BigDecimal,
                 rate: BigDecimal,
                 total: BigDecimal,
                 tradeId: Long,
                 `type`: String)

case class OrderNumber(orderNumber: Long,
                       resultingTrades: List[Trade])
