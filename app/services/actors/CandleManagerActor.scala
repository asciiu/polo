package services.actors

// external
import akka.actor.{Actor, ActorLogging}
import javax.inject.Inject
import play.api.Configuration
import scala.language.postfixOps

// internal
import models.market.MarketCandles
import models.poloniex.PoloniexEventBus
import models.poloniex.{MarketEvent, MarketCandle, MarketUpdate}


object CandleManagerActor {
  trait CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
}

/**
  * Created by bishop on 8/17/16. This actor is responsible for managing candles for all markets.
  */
class CandleManagerActor @Inject()(conf: Configuration) extends Actor with ActorLogging {
  import CandleManagerActor._
  import PoloniexCandleRetrieverActor._

  val eventBus = PoloniexEventBus()
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)

  val marketCandles = new MarketCandles()

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
    eventBus.subscribe(self, "/market/candles")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/update")
    eventBus.unsubscribe(self, "/market/candles")
  }

  def receive: Receive = {

    case update: MarketUpdate =>
      updateMarket(update)

    case GetCandles(name) =>
      sender ! marketCandles.getMarketCandles(name)

    case GetLastestCandle(name) =>
      sender ! marketCandles.getLatestCandle(name)

    case SetCandles(name, last24hrCandles) =>
      marketCandles.appendCandles(name, last24hrCandles)

  }

  private def updateMarket(update: MarketUpdate) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      if (!marketCandles.containsMarket(name)) {
        // send a message to the retriever to get the candle data from Poloniex
        // if the 24 hour baseVolume from this update is greater than our threshold
        if (update.info.baseVolume > baseVolumeRule) {
          eventBus.publish(MarketEvent("/market/added", QueueMarket(name)))
        }
      }

      marketCandles.updateMarket(update){ candleClose =>
        eventBus.publish(MarketEvent("/market/candle/close", candleClose))
      }
    }
  }
}
