package models.strategies

import models.analytics.individual.KitchenSink
import models.market.MarketStructures._
import models.poloniex.{MarketEvent, PoloniexEventBus}

/**
  * Publishes a notification when a market is setup for trade.
  * @param context
  */
class BollingerAlertStrategy(val context: KitchenSink) extends Strategy {

  val eventBus = PoloniexEventBus()

  var notificationPublished = false

  def handleMessage(msg: MarketMessage) = {
    context.getLatestPoints() match {
      case Some(bollinger) =>
        val currentPrice = msg.last

        // publish a notification of a good setup when this happens
        if (currentPrice > bollinger.center && currentPrice < bollinger.upper && !notificationPublished) {
          eventBus.publish(MarketEvent(PoloniexEventBus.BollingerNotification,  MarketSetupNotification(msg.cryptoCurrency, true)))
          notificationPublished = true
        } else if (currentPrice < bollinger.center && notificationPublished) {
          eventBus.publish(MarketEvent(PoloniexEventBus.BollingerNotification,  MarketSetupNotification(msg.cryptoCurrency, false)))
          notificationPublished = false
        }
      case None =>
    }
  }

  def train() = {}

  def reset(): Unit = {}

  def printResults(): Unit = {}
}
