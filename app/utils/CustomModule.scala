package utils

/**
  * Created by bishop on 9/5/16.
  */
import com.google.inject.AbstractModule
import models.market.TradeActor
import play.api.libs.concurrent.AkkaGuiceSupport
import services.{CandleManagerActor, ExponentialMovingAverageActor, VolumeTrackerActor}
import utils.poloniex.{PoloniexCandleRetrieverActor, PoloniexWebSocketClient}

class CustomModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bindActor[CandleManagerActor]("candle-actor")
    bindActor[ExponentialMovingAverageActor]("ema-actor")
    bindActor[PoloniexCandleRetrieverActor]("polo-candle-retriever")
    bindActor[PoloniexWebSocketClient]("polo-websocket-client")
    bindActor[TradeActor]("trade-actor")
    bindActor[VolumeTrackerActor]("volume-actor")
  }
}
