package models.poloniex

/**
  * Created by bishop on 9/8/16.
  */
case class CurrencyBalance(marketName: String,
                           available: BigDecimal,
                           onOrders: BigDecimal,
                           btcValue: BigDecimal)
