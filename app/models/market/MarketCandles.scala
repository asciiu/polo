package models.market

import java.time.OffsetDateTime

import com.typesafe.scalalogging.LazyLogging
import models.poloniex.{MarketCandle, MarketEvent, MarketUpdate}
import services.actors.ExponentialMovingAverageActor.MarketCandleClose
import services.actors.PoloniexCandleRetrieverActor.QueueMarket
import utils.Misc

import scala.collection.mutable.ListBuffer

/**
  * Created by bishop on 10/11/16.
  */
class MarketCandles(val periodMinutes: Int = 5) extends LazyLogging {
  require(periodMinutes >= 5)

  val marketCandles = scala.collection.mutable.Map[String, ListBuffer[MarketCandle]]()

  /**
    * Adds the market candles to collection.
    * @param marketName
    * @param last24hrCandles
    */
  def appendCandles(marketName: String, last24hrCandles: List[MarketCandle]) = {
    marketCandles.get(marketName) match {
      case Some(candles) =>
        logger.info(s"Retrieved candle data for $marketName")
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
        logger.info(s"Set candle data for $marketName")
        val candles = scala.collection.mutable.ListBuffer.empty[MarketCandle]
        candles.appendAll(last24hrCandles)
        marketCandles.put(marketName, candles)
    }
  }

  def getLatestCandle(marketName: String): Option[MarketCandle] = {
    marketCandles.get(marketName) match {
      case Some(list) => Some(list.head)
      case None => None
    }
  }

  def getMarketCandles(marketName: String): List[MarketCandle] = {
    marketCandles.get(marketName) match {
      case Some(list) => list.toList.reverse
      case None => List[MarketCandle]()
    }
  }

  def containsMarket(marketName: String): Boolean = {
    marketCandles.contains(marketName)
  }

  def updateMarket(update: MarketUpdate,
                   time: OffsetDateTime = Misc.currentTimeRoundedDown(periodMinutes))
                  (closeFn: (MarketCandleClose) => Unit) = {
    val name = update.name
    // only care about BTC markets
    if (name.startsWith("BTC")) {

      // this is the current time rounded down to 5 minutes
      //val time = Misc.currentTimeRoundedDown(periodMinutes)

      // get candle collection for this market
      val candleBuffer = marketCandles.get(name) match {
        case Some(candles) => candles
        case None =>
          val candles = scala.collection.mutable.ListBuffer.empty[MarketCandle]
          marketCandles.put(name, candles)
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
          val skippedCandles = ((time.toEpochSecond - currentCandle.time.toEpochSecond) / (periodMinutes * 60L)).toInt - 1
          for (i <- 1 to skippedCandles) {
            // add appropriate 5 minute interval to compute next candle time

            val candleTime = currentCandle.time.plusMinutes(periodMinutes * i)
            val candle = MarketCandle(candleTime, periodMinutes, currentCandle.close)

            // inform the moving average actor of new close period
            closeFn(MarketCandleClose(name, ClosePrice(candleTime, currentCandle.close)))
            //eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(candleTime, currentCandle.close))))
            candleBuffer.insert(0, candle)
          }

          // start a new candle
          val candle = MarketCandle(time, periodMinutes, update.info.last)
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
        val candle = MarketCandle(time, periodMinutes, update.info.last)
        candle.updateStatus(update.info)
        candleBuffer.append(candle)
      }

      closeFn(MarketCandleClose(name, ClosePrice(time, update.info.last)))
      //eventBus.publish(MarketEvent("/market/candle/close", MarketCandleClose(name, ClosePrice(time, update.info.last))))
    }
  }
}
