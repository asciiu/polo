package utils.poloniex

/**
  * Created by bishop on 8/17/16.
  */
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.poloniex.{Market, MarketCandle, MarketStatus}
import org.joda.time._

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

object PoloniexCandleCreatorActor {
  def props()(implicit system: ActorSystem): Props = Props(new PoloniexCandleCreatorActor())

  trait CandleCreatorMessage
  case class GetCandles(marketName: String) extends CandleCreatorMessage
  case class GetLastestCandle(marketName: String) extends CandleCreatorMessage
}

class PoloniexCandleCreatorActor(implicit system: ActorSystem) extends Actor with ActorLogging {
  import PoloniexCandleCreatorActor._

  val eventBus = PoloniexEventBus()
  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()
  val movingAverages = scala.collection.mutable.Map[String, (BigDecimal, BigDecimal)]()

  // TODO moving averages needs to be implemented probably in another class
  // try 15 and 7 as configurable periods
  // interval can be configured as 5 min, 15 min, 30 min, 1 hr, 2 hr

  // 30 minutes should really be 6
  // start with 5 minute interval or 1 candle period
  var interval = 1
  var emaPeriod1 = 15
  var emaPeriod2 = 7
  def multiplier(period: Int) : BigDecimal = {
    2 / (period + 1 )
  }

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
  }

  def receive: Receive = {

    case update: Market => recordUpdate(update)

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
  }

  def updateEMA(name: String, candles: List[MarketCandle]) = {
    // get latest candle from head
    val candle = candles.head
    val minutes = candle.timeIntervalMinutes
    // latest period
    val time = candle.time

    // minutes * 60000 ms per minute
    val time1 = time.getMillis() - (minutes * 60000) * emaPeriod1
    val time2 = time.getMillis() - (minutes * 60000) * emaPeriod2

    // has enough time elapsed yet
    if (candles.last.time.getMillis() == time1) {
      // get all candles with time greater than equeal to time and sum the close price
      val sum1 = candles.filter(c => c.time.getMillis() >= time1).map(_.close).sum
      // set ema1 for latest candle
      candle.ema1 = sum1 / emaPeriod1
    }
    // if the next candle in our list has an ema1 use it to calc this ema1
    // number of candles we have should be more than the emaPeriod1 in order for this
    // condition to hold. This means that
    else if (candles.last.time.getMillis() < time1) {
      val nextCandle = candles(1)
      //{Close - EMA(previous day)} x multiplier + EMA(previous day)
      val ema1 = (candle.close - nextCandle.ema1) * multiplier(emaPeriod1) + nextCandle.ema1
      candle.ema1 = ema1
      log.info(s"$name EMA1 - $ema1")
    }

    if (candles.last.time.getMillis() == time2) {
      val sum2 = candles.filter(c => c.time.getMillis() >= time2).map(_.close).sum
      candle.ema2 = sum2 / emaPeriod2
    }
    else if (candles.last.time.getMillis() < time2) {
      val nextCandle = candles(1)
      val ema2 = (candle.close - nextCandle.ema2) * multiplier(emaPeriod2) + nextCandle.ema2
      candle.ema2 = ema2
      log.info(s"$name EMA2 - $ema2")
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
          val lastCandle = candles.head
          val now = new DateTime()

          // are we still in the time period of the last candle that we created
          if (lastCandle.isTimeInterval(now)) {
            // if so we need to update the info for the last candle
            lastCandle.updateInfo(update.status)
          } else {

            // if we've skipped some candle periods due to no trade activity
            // we need to fill in those missed periods so we can calc the
            // simple moving average
            val missedCandlesNum = lastCandle.periodDif(now) - 1
            if (missedCandlesNum > 0) {
              for (i <- 1 to missedCandlesNum) {
                val time = new DateTime(lastCandle.time.getMillis + (60000 * i))
                val candle = MarketCandle(time, 5, lastCandle.close)
                candles.insert(0, candle)
              }
            }

            // start a new candle
            val timePeriod = MarketCandle.roundDateToMinute(now, 5)
            val candle = MarketCandle(timePeriod, 5, update.status.last)
            candle.updateInfo(update.status)
            // most recent candles at front list
            candles.insert(0, candle)

            // limit candle buffer length to 24 hours
            if (candles.length > 288) candles.remove(288)

            // compute the expo moving averages
            updateEMA(name, candles.toList)
          }
        case None =>
          // init first 5 minute candle for this market
          val timePeriod = MarketCandle.roundDateToMinute(new DateTime(), 5)
          val candle = MarketCandle(timePeriod, 5, update.status.last)
          candle.updateInfo(update.status)
          val candles =  scala.collection.mutable.ListBuffer.empty[MarketCandle]
          candles.append(candle)
          marketCandles.put(name, candles)
      }
    }
  }
}
