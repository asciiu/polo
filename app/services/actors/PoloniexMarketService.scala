package services.actors

// external
import akka.actor.{Actor, ActorLogging, ActorRef}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

// internal
import models.market.MarketStructures.Candles
import models.market.MarketStructures.MarketMessage
import models.poloniex.PoloniexEventBus
import models.poloniex.PoloniexEventBus._
import services.DBService


object PoloniexMarketService {
  trait CandleManagerMessage

  // TODO these should be implemented in the MarketService
  case object GetSessionId extends CandleManagerMessage
  case object StartCapture extends CandleManagerMessage
  case object EndCapture extends CandleManagerMessage
}

/**
  * This actor is reponsible for managing all poloniex markets. New
  * actors for each market should be created here.
  */
class PoloniexMarketService @Inject()(val database: DBService,
                                      ws: WSClient,
                                      conf: Configuration)(implicit ctx: ExecutionContext) extends Actor
  with ActorLogging {

  import PoloniexMarketService._
  import PoloniexCandleRetrieverService._

  val candleService = context.actorOf(PoloniexCandleRetrieverService.props(ws, conf))
  val markets = scala.collection.mutable.Map[String, ActorRef]()

  val eventBus = PoloniexEventBus()
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, Updates)
  }

  override def postStop() = {
    eventBus.unsubscribe(self, Updates)
  }

  def receive = myReceive

  def myReceive: Receive = {
    case mc: Candles =>
      markets(mc.marketName) ! mc

    case msg: MarketMessage =>
      val marketName = msg.cryptoCurrency

      // only care about BTC markets
      if (marketName.startsWith("BTC") && !markets.contains(marketName) &&
        msg.baseVolume > baseVolumeRule) {

        // fire up a new actor per market
        markets += marketName -> context.actorOf(MarketService.props(marketName, database), marketName)

        // send a message to the retriever to get the candle data from Poloniex
        // if the 24 hour baseVolume from this update is greater than our threshold
        //eventBus.publish(MarketEvent(NewMarket, QueueMarket(marketName)))
        candleService ! QueueMarket(marketName)
      }

      if (markets.contains(marketName)) {
        markets(marketName) ! msg
      }

    case GetSessionId =>
      //sender ! getSessionId

    // TODO send the session id to the actors
      // and have the actors archive the candles
    case StartCapture =>
      //beginSession(marketCandles.map( m => Can(m._1, m._2.toList)).toList)
    case EndCapture =>
      //endSession()
  }
}
