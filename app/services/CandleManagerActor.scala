package services

// external
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import org.joda.time._
import play.api.libs.ws.WSClient
import utils.poloniex.{MarketEvent, PoloniexCandleRetrieverActor, PoloniexEventBus}

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

// internal
import models.poloniex.{Market, MarketCandle}


object CandleManagerActor {
  trait CandleManagerMessage
  case class GetCandles(marketName: String) extends CandleManagerMessage
  case class GetLastestCandle(marketName: String) extends CandleManagerMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleManagerMessage
}

/**
  * Created by bishop on 8/17/16. This actor is responsible for managing candles for all markets.
  */
class CandleManagerActor @Inject() extends Actor with ActorLogging {
  import CandleManagerActor._
  import PoloniexCandleRetrieverActor._
  import models.market.ClosePrice
  import services.ExponentialMovingAverageActor.MarketCandleClose

  val eventBus = PoloniexEventBus()
  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()
  val candleTimeMinutes = 5

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

    case update: Market =>
      updateMarket(update)

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
          // This assumes that the candles received are sorted by time descending
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

  private def updateMarket(update: Market) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      // this is the current time rounded down to 5 minutes
      val time = MarketCandle.roundDateToMinute(new DateTime(), candleTimeMinutes)

      // get candle collection for this market
      val candleBuffer = marketCandles.get(name) match {
        case Some(candles) => candles
        case None =>
          val candles =  scala.collection.mutable.ListBuffer.empty[MarketCandle]
          marketCandles.put(name, candles)

          // send a message to the retriever to get the candle data from Poloniex
          eventBus.publish(MarketEvent("/market/added", QueueMarket(name)))
          candles
      }

      if (candleBuffer.length > 0) {
        // examine candle at head
        val currentCandle = candleBuffer.head

        // are we still in the time period of the last candle that we created
        if (currentCandle.time.isEqual(time)) {
          // if so we need to update the info for the last candle
          currentCandle.updateStatus(update.status)
        } else {
          // if we've skipped some candle periods due to no trade activity
          // we need to fill in those missed periods
          val skippedCandles = ((time.getMillis - currentCandle.time.getMillis) / (candleTimeMinutes * 60000L)).toInt - 1
          for (i <- 1 to skippedCandles) {
            // add appropriate 5 minute interval to compute next candle time
            val candleTime = new DateTime(currentCandle.time.getMillis + (candleTimeMinutes * 60000L * i))
            val candle = MarketCandle(candleTime, candleTimeMinutes, currentCandle.close)

            // inform the moving average actor of new close period
            eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(candleTime, currentCandle.close))))
            candleBuffer.insert(0, candle)
          }

          // start a new candle
          val candle = MarketCandle(time, candleTimeMinutes, update.status.last)
          candle.updateStatus(update.status)
          // most recent candles at front list
          candleBuffer.insert(0, candle)

          // limit candle buffer length to 24 hours
          if (candleBuffer.length > 288) {
            val removeNum = candleBuffer.length - 288
            candleBuffer.remove(288, removeNum)
          }
        }

      } else {
        val candle = MarketCandle(time, candleTimeMinutes, update.status.last)
        candle.updateStatus(update.status)
        candleBuffer.append(candle)
      }

      eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(time, update.status.last))))
    }
  }
}
