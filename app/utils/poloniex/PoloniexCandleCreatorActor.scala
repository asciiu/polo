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
  // interval can be configured as 5, 15, 30, 1hr, 2hr
  var interval = 30
  var p1 = 15
  var p2 = 7
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
    val candle = candles.head
    val currentTimePeriod = candle.time
    val period1 = currentTimePeriod.getMillis() - (candle.timeIntervalMinutes * 60000) * p1
    val period2 = currentTimePeriod.getMillis() - (candle.timeIntervalMinutes * 60000) * p2

    // has enough time elapsed yet
    if (candles.last.time.getMillis() <= period1) {
      val sum1 = candles.filter(c => c.time.getMillis() >= period1).map(_.close).sum
      val sum2 = candles.filter(c => c.time.getMillis() >= period2).map(_.close).sum
      println(name)
      println(s" ${sum1 / p1}")
      println(s" ${sum2 / p2}")
      println()
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
