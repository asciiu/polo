package models.strategies

import akka.actor.Actor
import models.market.MarketStructures.MarketMessage

/**
  * Created by bishop on 10/17/16.
  */
trait TheoreticalPerfectStrategy {
  self: Actor =>

  var balance: BigDecimal = 1.0
  val markets = scala.collection.mutable.Map[String, BigDecimal]()
  val buyRecords = scala.collection.mutable.Map[String, BigDecimal]()

  // TODO this should perform a perfect by low sell high
  def handleMessageUpdate: Receive = {
    case msg: MarketMessage =>
      val marketName = msg.cryptoCurrency
      val currentPrice = msg.last

      if (markets.contains(marketName)) {
        val previousPrice = markets(marketName)

        // if the update price is greater than the previous price
        // update the stored price
        if (previousPrice < currentPrice) {

          // store a record for the lowest price
          if (!buyRecords.contains(marketName) && balance > previousPrice) {
            buyRecords(marketName) = previousPrice
            balance -= previousPrice
          }

        } else if (previousPrice > currentPrice) {

          // we should have sold at the previous price
          if (buyRecords.contains(marketName)) {
            buyRecords.remove(marketName)
            balance += previousPrice
          }

          markets(marketName) = currentPrice
        }
      }
  }
}
