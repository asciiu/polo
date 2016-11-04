package services.actors

// external

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import java.time.OffsetDateTime
import javax.inject.Inject

import models.analytics.theworks.{ExponentialMovingAverages, MarketCandles, Volume24HourTracking}
import models.analytics.AccountBalances
import models.market.MarketStructures.{Candles, ClosePrice, ExponentialMovingAverage}
import models.strategies.{FirstCrossStrategy}
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

// internal
import models.market.MarketStructures.MarketMessage
import models.market.MarketStructures.{Candles => Can}
import models.poloniex.PoloniexEventBus
import models.poloniex.PoloniexEventBus._
import models.poloniex.{MarketEvent}
import models.market.MarketCandle
import models.analytics.Archiving
import services.DBService


object PoloniexMarketService {
  trait CandleManagerMessage
  case object GetSessionId extends CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
  case object StartCapture extends CandleManagerMessage
  case object EndCapture extends CandleManagerMessage
  case class GetLatestMovingAverages(marketname: String) extends CandleManagerMessage
  case class GetMovingAverages(marketName: String) extends CandleManagerMessage
  case class GetVolume(marketName: String, time: OffsetDateTime) extends CandleManagerMessage
  case class GetVolumes(marketName: String) extends CandleManagerMessage
  case class GetLatestMessage(marketName: String) extends CandleManagerMessage
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
  import MarketService._

  val candleService = context.actorOf(PoloniexCandleRetrieverService.props(ws, conf))
  val markets = scala.collection.mutable.Map[String, ActorRef]()

  // TODO this will belong in the market actors when that is fully factored out
  //val strategy = new FirstCrossStrategy(this)

  val eventBus = PoloniexEventBus()
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, Updates)
  }

  override def postStop() = {
    eventBus.unsubscribe(self, Updates)
    //strategy.printResults()
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
        markets += marketName -> context.actorOf(MarketService.props(marketName, database))

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

    case GetCandles(marketName) =>
      markets(marketName) ! SendCandles(sender)
      //sender ! getMarketCandles(marketName)

    case GetLastestCandle(marketName) =>
      markets(marketName) ! SendLatestCandle(sender)
      //sender ! getLatestCandle(marketName)

    case SetCandles(marketName, candles) =>
//      appendCandles(marketName, candles)
//      val closePrices = candles.map( c => ClosePrice(c.time, c.close))
//      setAverages(marketName, closePrices)
//      sender ! allAverages(marketName).map( a => (a.period, a.emas))

    case GetLatestMovingAverages(marketName) =>
      markets(marketName) ! SendLatestMovingAverages(sender)
      //sender ! getLatestMovingAverages(marketName)

    case GetMovingAverages(marketName) =>
      markets(marketName) ! SendMovingAverages(sender)
      //sender ! getMovingAverages(marketName)

    case GetVolume(marketName, time) =>
      markets(marketName) ! SendVolume(sender, time)
      //sender ! getVolume(marketName, time)

    case GetVolumes(marketName) =>
      markets(marketName) ! SendVolumes(sender)

    case GetLatestMessage(marketName) =>
      markets(marketName) ! SendLatestMessage(sender)
  }
}
