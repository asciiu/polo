package utils.poloniex

/**
  * Created by bishop on 8/17/16.
  */
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.poloniex.{Market, MarketCandle, MarketStatus}
import org.joda.time._
import play.api.libs.ws.WSClient
import services.ClosePrice
import services.ExponentialMovingAverageActor.MarketCandleClose

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

object PoloniexCandleManagerActor {
  def props(ws: WSClient)(implicit system: ActorSystem): Props = Props(new PoloniexCandleManagerActor(ws))

  trait CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
}

class PoloniexCandleManagerActor(ws: WSClient)(implicit system: ActorSystem) extends Actor with ActorLogging {
  import PoloniexCandleManagerActor._
  import PoloniexCandleRetrieverActor._

  val eventBus = PoloniexEventBus()
  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()

  object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  }
  import Joda._

  val candleDataRetriever = context.actorOf(PoloniexCandleRetrieverActor.props(ws), "Poloniex-candle-retriever")


  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
  }

  def receive: Receive = {

    case update: Market =>
      recordUpdate(update)

    case GetCandles(name) =>
      marketCandles.get(name) match {
        case Some(list) =>
          sender ! list.toList.take(70).reverse
        case None =>
          sender ! List[MarketCandle]()
      }

    case GetLastestCandle(name) =>
      marketCandles.get(name) match {
        case Some(list) =>
          sender ! Some(list(0))
        case None =>
          sender ! None
      }

    case SetCandles(name, last24hrCandles) =>
      marketCandles.get(name) match {
        case Some(candles) =>
          log.info(s"Retrieved candle data for $name")
          val lastCandle = last24hrCandles.head
          val currentCandle = candles.last
          if (currentCandle.time.equals(lastCandle.time)) {
            currentCandle.addCandle(lastCandle)
            candles.appendAll(last24hrCandles.takeRight(last24hrCandles.length - 1))
          } else {
            candles.appendAll(last24hrCandles)
          }
        case None =>
          log.error(s"received $name ${last24hrCandles.length} but nowhere to add them??")
      }
  }

  def recordUpdate(update: Market) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      // todo maybe this publish should be elsewhere?
      val time = MarketCandle.roundDateToMinute(new DateTime(), 5)
      val newCandleClose = MarketCandleClose(name, ClosePrice(time, update.status.last))

      // do we have candles for this market?
      marketCandles.get(name) match {
        case Some(candles) =>
          // examine candle at head
          val currentCandle = candles.head
          val now = new DateTime()
          //val currentPeriod = MarketCandle.roundDateToMinute(now, 5)

          // are we still in the time period of the last candle that we created
          if (currentCandle.time.isEqual(time)) {
            // if so we need to update the info for the last candle
            currentCandle.updateStatus(update.status)
          } else {

            // if we've skipped some candle periods due to no trade activity
            // we need to fill in those missed periods so we can calc the
            // simple moving average
            val skipped = ((time.getMillis() - currentCandle.time.getMillis()) / (5 * 60000)).toInt - 1
            if (skipped > 0) {
              for (i <- 1 to skipped) {
                val previousCandle = candles(1)
                val t = new DateTime(currentCandle.time.getMillis + (5 * 60000 * i))
                val candle = MarketCandle(time, 5, currentCandle.close)

                // TODO this should work idk yet
                eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(t, currentCandle.close))))
                candles.insert(0, candle)
              }
            }

            // start a new candle
            //val timePeriod = MarketCandle.roundDateToMinute(now, 5)
            val candle = MarketCandle(time, 5, update.status.last)
            candle.updateStatus(update.status)
            // most recent candles at front list
            candles.insert(0, candle)

            // limit candle buffer length to 24 hours
            if (candles.length > 288) candles.remove(288)
          }
        case None =>
          // init first 5 minute candle for this market
          //val timePeriod = MarketCandle.roundDateToMinute(new DateTime(), 5)
          val candle = MarketCandle(time, 5, update.status.last)
          candle.updateStatus(update.status)
          val candles =  scala.collection.mutable.ListBuffer.empty[MarketCandle]
          candles.append(candle)
          marketCandles.put(name, candles)
          // send a message to the retriever to get the candle data from Poloniex
          candleDataRetriever ! QueueMarket(name)
      }

      eventBus.publish(MarketEvent("/market/candle/close", newCandleClose))
    }
  }
}
