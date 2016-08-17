package utils.poloniex

/**
  * Created by bishop on 8/17/16.
  */
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.poloniex.{Market, MarketCandle, MarketStatus}

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

object PoloniexCandleCreatorActor {
  def props()(implicit system: ActorSystem): Props = Props(new PoloniexCandleCreatorActor())
}

class PoloniexCandleCreatorActor(implicit system: ActorSystem) extends Actor with ActorLogging {
  val eventBus = PoloniexEventBus()
  val tickers = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()

  override def preStart() = {
    log info "subscribed to market updates"
    eventBus.subscribe(self, "/market/update")
  }

  def receive: Receive = {
    case update: Market => recordUpdate(update)
  }

  def recordUpdate(update: Market) = {
    import org.joda.time._

    def roundDateDownToMinute(dateTime: DateTime, minutes: Int): DateTime = {
      if (minutes < 1 || 5 % minutes != 0) {
        throw new IllegalArgumentException("minutes must be a factor of 5")
      }

      val m = dateTime.getMinuteOfHour() / minutes
      new DateTime(dateTime.getYear(),
        dateTime.getMonthOfYear(),
        dateTime.getDayOfMonth,
        dateTime.getHourOfDay(),
        m * minutes
      )
    }

    val ticker = update.ticker
    // only care about BTC markets
    if (ticker.startsWith("BTC")) {
      // get existing ticker from map
      tickers.get(ticker) match {
        case Some(candles) =>
          val c = candles.head
          val now = new DateTime()

          if (c.isTimeInterval(now)) {
            c.updateInfo(update.status)
          } else {
            // basevolume must be greater than 70
            if (c.volumeBtc24Hr > 70.0 && c.isBuy) {
              val percentChange = (c.close - c.open) / c.open * 100
              val stem = (c.close - c.low) / c.open * 100
              //println(s"${c.time} $ticker HIGH: ${c.high} LOW: ${c.low} CLOSE: ${c.close} BTC24VOLUME: ${c.volumeBtc24Hr}")
              // if percenChange is < 0.05% and the lowest is lower than the close don't buy
              println(s"${c.time} $ticker ${percentChange.setScale(2, RoundingMode.CEILING)}% CLOSE: ${c.close} BTC24VOLUME: ${c.volumeBtc24Hr}")
            }
            // start a new candle
            val candle = MarketCandle(MarketCandle.roundDateToMinute(now, 5), 5, update.status.last)
            candle.updateInfo(update.status)
            candles.insert(0, candle)

            // limit candle buffer length to 24 hours
            if (candles.length > 288) {
              candles.remove(288)
            }
          }
        // was there a change in the price?
        //val delta = update.status.last - previousUpdate.last
        //val volume = update.baseVolume + previousUpdate.baseVolume

        //if (delta > 0) {
        //  val now = new DateTime()
        //  println(s"$now $ticker")
        //  val timeperiod = roundDateDownToMinute(now, 5)
        //  println(s"$timeperiod last ${update.status.last}")
        //  println()
        //  // two things you need to know very quickly about the update
        //  // what market ticker is it, the current time interval
        //  // if a new time interval we start a new candle
        //  // if in a current time interval we have to update the current candle
        //}
        //tickers.put(ticker, update.status)
        case None =>
          // init first 5 minute candle for this market
          val time = MarketCandle.roundDateToMinute(new DateTime(), 5)
          val candle = MarketCandle(time, 5, update.status.last)
          candle.updateInfo(update.status)
          val buffer = scala.collection.mutable.ListBuffer.empty[MarketCandle]
          buffer.append(candle)
          tickers.put(ticker, buffer)
      }
    }
  }
}
