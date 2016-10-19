package utils

/**
  * Created by bishop on 9/5/16.
  */
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.actors._

class CustomModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    // for live traffic
    bindActor[CandleManagerActor]("candle-actor")
    bindActor[PoloniexCandleRetrieverActor]("polo-candle-retriever")
    bindActor[PoloniexWebSocketClient]("polo-websocket-client")
    bindActor[TradeActor]("trade-actor")
    bindActor[VolumeTrackerActor]("volume-actor")
  }
}
