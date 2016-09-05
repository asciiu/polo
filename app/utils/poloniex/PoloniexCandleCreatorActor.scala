package utils.poloniex

/**
  * Created by bishop on 8/17/16.
  */
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.poloniex.{Market, MarketCandle, MarketStatus}
import org.joda.time._
import play.api.libs.ws.WSClient

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

object PoloniexCandleCreatorActor {
  def props(ws: WSClient)(implicit system: ActorSystem): Props = Props(new PoloniexCandleCreatorActor(ws))

  trait CandleCreatorMessage
  case class GetCandles(marketName: String) extends CandleCreatorMessage
  case class GetLastestCandle(marketName: String) extends CandleCreatorMessage
  case class SetCandles(marketName: String, candles: List[MarketCandle]) extends CandleCreatorMessage
}

class PoloniexCandleCreatorActor(ws: WSClient)(implicit system: ActorSystem) extends Actor with ActorLogging {
  import PoloniexCandleCreatorActor._
  import PoloniexCandleRetrieverActor._

  val eventBus = PoloniexEventBus()
  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()
  val movingAverages = scala.collection.mutable.Map[String, (BigDecimal, BigDecimal)]()
  object Joda {
    implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  }
  import Joda._
  // Needed to retrieve candle data from Poloniex on start up
  val candleDataRetriever = context.actorOf(PoloniexCandleRetrieverActor.props(ws), "Poloniex-candle-retriever")

  // TODO moving averages needs to be implemented probably in another class
  // try 15 and 7 as configurable periods
  // interval can be configured as 5 min, 15 min, 30 min, 1 hr, 2 hr

  // 30 minutes should really be 6
  // start with 5 minute interval or 1 candle period
  var interval = 1
  var emaPeriod1 = 15
  var emaPeriod2 = 7

  def multiplier(period: Int) : BigDecimal = {
    BigDecimal(2.0 / (period + 1))
  }

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
  }

  def receive: Receive = {

    case update: Market =>
      recordUpdate(update)
      updateEMA(update.name)

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
            updateAllEMA(name)
          } else {
            candles.appendAll(last24hrCandles)
            updateAllEMA(name)
          }
        case None =>
          log.error(s"received $name ${last24hrCandles.length} but nowhere to add them??")

      }
  }

  def updateAllEMA(name: String) = {
    marketCandles.get(name) match {
      case Some(candles) =>
        val sum1 = candles.takeRight(emaPeriod1).map(_.close).sum
        val sma1 = sum1 / emaPeriod1
        candles(candles.length-emaPeriod1).ema1 = sma1
        val sum2 = candles.takeRight(emaPeriod2).map(_.close).sum
        val sma2 = sum2 / emaPeriod2
        candles(candles.length-emaPeriod2).ema2 = sma2

        // update the ema1 and ema2
        for (i <- emaPeriod1 until candles.length-1) {
          val previousCandle = candles(candles.length - i)
          val candle = candles(candles.length - (i+1))
          val ema1 = (candle.close - previousCandle.ema1) * multiplier(emaPeriod1) + previousCandle.ema1
          candle.ema1 = ema1.setScale(8, RoundingMode.CEILING)
        }

        for (i <- emaPeriod2+1 until candles.length-1) {
          val previousCandle = candles(candles.length - i)
          val candle = candles(candles.length - (i+1))
          val ema2 = (candle.close - previousCandle.ema2) * multiplier(emaPeriod2) + previousCandle.ema2
          candle.ema2 = ema2.setScale(8, RoundingMode.CEILING)
        }
      case None =>
    }
  }
  def updateEMA(name: String) = {
    marketCandles.get(name) match {
      case Some(candles) =>
        val candle = candles.head
        if (candles.length > emaPeriod1) {
        // get latest candle from head

        // has enough time elapsed yet
        //if (candles.length == emaPeriod1) {
        //  // get all candles with time greater than equeal to time and sum the close price
        //  val sum1 = candles.map(_.close).sum
        //  // first ema1 will be the simple moving average
        //  candle.ema1 = (sum1 / emaPeriod1).setScale(8, RoundingMode.CEILING)
        //}
        // if the next candle in our list has an ema1 use it to calc this ema1
        // number of candles we have should be more than the emaPeriod1 in order for this
        // condition to hold. This means that
        //if (candles.length > emaPeriod1) {
          val previousCandle = candles(1)
          //{Close - EMA(previous day)} x multiplier + EMA(previous day)
          val ema1 = (candle.close - previousCandle.ema1) * multiplier(emaPeriod1) + previousCandle.ema1
          candle.ema1 = ema1.setScale(8, RoundingMode.CEILING)
        }

        if (candles.length > emaPeriod2) {
          //log.info(s"$name EMA1 - $ema1")
        //}

        //if (candles.length == emaPeriod2) {
        //  val sum2 = candles.map(_.close).sum
        //  candle.ema2 = (sum2 / emaPeriod2).setScale(8, RoundingMode.CEILING)
        //}
        //else if (candles.length > emaPeriod2) {
          val previousCandle = candles(1)
          val ema2 = (candle.close - previousCandle.ema2) * multiplier(emaPeriod2) + previousCandle.ema2
          candle.ema2 = ema2.setScale(8, RoundingMode.CEILING)
          //log.info(s"$name EMA2 - $ema2")
        }
      case None =>
    }
  }

  def recordUpdate(update: Market) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      // do we have candles for this market?
      marketCandles.get(name) match {
        case Some(candles) =>
          // examine candle at head
          val currentCandle = candles.head
          val now = new DateTime()
          val currentPeriod = MarketCandle.roundDateToMinute(now, 5)

          // are we still in the time period of the last candle that we created
          if (currentCandle.time.isEqual(currentPeriod)) {
            // if so we need to update the info for the last candle
            currentCandle.updateStatus(update.status)
          } else {

            // if we've skipped some candle periods due to no trade activity
            // we need to fill in those missed periods so we can calc the
            // simple moving average
            val skipped = ((currentPeriod.getMillis() - currentCandle.time.getMillis()) / (5 * 60000)).toInt - 1
            if (skipped > 0) {
              for (i <- 1 to skipped) {
                val previousCandle = candles(1)
                val time = new DateTime(currentCandle.time.getMillis + (5 * 60000 * i))
                val candle = MarketCandle(time, 5, currentCandle.close)
                val ema1 = (candle.close - previousCandle.ema1) * multiplier(emaPeriod1) + previousCandle.ema1
                val ema2 = (candle.close - previousCandle.ema2) * multiplier(emaPeriod2) + previousCandle.ema2
                candle.ema1 = ema1.setScale(8, RoundingMode.CEILING)
                candle.ema2 = ema2.setScale(8, RoundingMode.CEILING)
                candles.insert(0, candle)
              }
            }

            // start a new candle
            //val timePeriod = MarketCandle.roundDateToMinute(now, 5)
            val candle = MarketCandle(currentPeriod, 5, update.status.last)
            candle.updateStatus(update.status)
            // most recent candles at front list
            candles.insert(0, candle)

            // limit candle buffer length to 24 hours
            if (candles.length > 288) candles.remove(288)
          }
        case None =>
          // init first 5 minute candle for this market
          val timePeriod = MarketCandle.roundDateToMinute(new DateTime(), 5)
          val candle = MarketCandle(timePeriod, 5, update.status.last)
          candle.updateStatus(update.status)
          val candles =  scala.collection.mutable.ListBuffer.empty[MarketCandle]
          candles.append(candle)
          marketCandles.put(name, candles)
          // send a message to the retriever to get the candle data from Poloniex
          candleDataRetriever ! QueueMarket(name)

      }
    }
  }
}
