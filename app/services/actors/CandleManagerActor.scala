package services.actors

// external
import java.time.OffsetDateTime
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import models.poloniex.{MarketEvent, PoloniexEventBus}
import org.joda.time._
import models.poloniex.MarketEvent
import play.api.Configuration

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

// internal
import models.poloniex.{MarketCandle, MarketUpdate}
import services.actors.ExponentialMovingAverageActor.MarketCandleClose


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
  import models.market.ClosePrice

  val eventBus = PoloniexEventBus()
  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()
  val candleTimeMinutes = 5
  val baseVolumeRule = conf.getInt("poloniex.candle.baseVolume").getOrElse(500)

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
      marketCandles.get(name) match {
        case Some(list) =>
          sender ! list.toList.reverse
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

  private def updateMarket(update: MarketUpdate) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      // this is the current time rounded down to 5 minutes
      val time = MarketCandle.roundDateToMinute(OffsetDateTime.now(), candleTimeMinutes)

      // get candle collection for this market
      val candleBuffer = marketCandles.get(name) match {
        case Some(candles) => candles
        case None =>
          val candles = scala.collection.mutable.ListBuffer.empty[MarketCandle]
          marketCandles.put(name, candles)

          // send a message to the retriever to get the candle data from Poloniex
          // if the 24 hour baseVolume from this update is greater than our threshold
          if (update.info.baseVolume > baseVolumeRule) {
              eventBus.publish(MarketEvent("/market/added", QueueMarket(name)))
          }
          candles
      }

      if (candleBuffer.length > 0) {
        // examine candle at head
        val currentCandle = candleBuffer.head

        // are we still in the time period of the last candle that we created
        if (currentCandle.time.isEqual(time)) {
          // if so we need to update the info for the last candle
          currentCandle.updateStatus(update.info)
        } else {
          // if we've skipped some candle periods due to no trade activity
          // we need to fill in those missed periods
          val skippedCandles = ((time.toEpochSecond - currentCandle.time.toEpochSecond) / (candleTimeMinutes * 60L)).toInt - 1
          for (i <- 1 to skippedCandles) {
            // add appropriate 5 minute interval to compute next candle time

            val candleTime = currentCandle.time.plusMinutes(candleTimeMinutes * i)
            val candle = MarketCandle(candleTime, candleTimeMinutes, currentCandle.close)

            // inform the moving average actor of new close period
            eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(candleTime, currentCandle.close))))
            candleBuffer.insert(0, candle)
          }

          // start a new candle
          val candle = MarketCandle(time, candleTimeMinutes, update.info.last)
          candle.updateStatus(update.info)
          // most recent candles at front list
          candleBuffer.insert(0, candle)

          // limit candle buffer length to 24 hours
          if (candleBuffer.length > 288) {
            val removeNum = candleBuffer.length - 288
            candleBuffer.remove(288, removeNum)
          }
        }

      } else {
        val candle = MarketCandle(time, candleTimeMinutes, update.info.last)
        candle.updateStatus(update.info)
        candleBuffer.append(candle)
      }

      eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(time, update.info.last))))
    }
  }
}
