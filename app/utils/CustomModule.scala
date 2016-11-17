package utils

/**
  * Created by bishop on 9/5/16.
  */
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.actors._
import services.actors.orderbook.PoloniexOrderBookManager

class CustomModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    // for live traffic
    bindActor[PoloniexOrderBookManager]("poloniex-orderbooks")
    bindActor[PoloniexMarketService]("poloniex-market")
    bindActor[PoloniexWebSocketFeedService]("poloniex-feed")
    bindActor[NotificationService]("poloniex-alerts")
  }
}
