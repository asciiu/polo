package models.analytics

/**
  * Created by bishop on 10/27/16.
  */
trait AccountBalances {

  var balance = BigDecimal(1.0)

  val balancesByMarket = scala.collection.mutable.Map[String, BigDecimal]()
  val btcBalancesByMarket = scala.collection.mutable.Map[String, BigDecimal]()

  def addBTCBalance(btcAmount: BigDecimal) = balance += btcAmount
  def getBTCBalance = balance
  def setBTC(btcAmount: BigDecimal) = balance = btcAmount
  def subtractBTCBalance(btcAmount: BigDecimal) = balance -= btcAmount

  def addMarketBalance(marketName: String, quantity: BigDecimal) = {
    val marketBalance: BigDecimal = balancesByMarket.getOrElse(marketName, 0.0)
    balancesByMarket(marketName) = marketBalance + quantity
  }

  def subtractMarketBalance(marketName: String, quantity: BigDecimal) = {
    val marketBalance: BigDecimal = balancesByMarket.getOrElse(marketName, 0.0)
    balancesByMarket(marketName) = marketBalance - quantity
  }

  def getMarketBalance(marketName: String) = balancesByMarket.getOrElse(marketName, BigDecimal(0.0))

  def getTotalBalance(): BigDecimal = {
    val inventory = btcBalancesByMarket.map(_._2).sum
    balance + inventory
  }
}
