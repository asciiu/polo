package models.analytics.individual

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner

import models.market.MarketCandle
import models.market.MarketStructures.{Candles, ClosePrice, MarketMessage}
import scala.collection.mutable.ListBuffer
import utils.Misc._


trait MarketCandles extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      updateMarketCandle(ClosePrice(msg.time, msg.last))
      Inner(msg)

    /**
      * Candles must be orderd by latest time first!
      */
    case mc: Candles =>
      appendCandles(mc.candles)
      Inner(mc)
  }

  val marketName: String
  val periodMinutes: Int = 5
  val marketCandles = ListBuffer[MarketCandle]()

  /**
    * Adds the market candles to the map of candles. Assumes candles are ordered by time descending.
    * This class also assumes that the candle periods in the last 24 hr candles collection is uniform
    * and equal to the periodMinutes stated in this trait.
    *
    * @param last24hrCandles last 24 hour candles 5 minute periods
    */
  def appendCandles(last24hrCandles: List[MarketCandle]) = {
    if (marketCandles.nonEmpty) {
      // merge identical candles if needed
      for (candle <- marketCandles) {
        last24hrCandles.find(_.isTimePeriod(candle.time)) match {
          case Some(c) => candle += c
          case None =>
        }
      }

      val tail = last24hrCandles.dropWhile(c => c.time.isAfter(marketCandles.last.time) || c.time.isEqual(marketCandles.last.time))
      marketCandles.appendAll(tail)
    } else {
      marketCandles.appendAll(last24hrCandles)
    }

    log.info(s"Candle data for $marketName added")
  }

  /**
    * Returns the lastest market candle.
    *
    * @return optional candle none if there are no candles
    */
  def getLatestCandle(): Option[MarketCandle] = {
    marketCandles.headOption
  }

  /**
    * Returns the list of candles ordered by time.
    *
    * @return
    */
  def getCandles(): List[MarketCandle] = {
    marketCandles.reverse.toList
  }

  def updateMarketCandle(closePrice: ClosePrice) = {
    marketCandles.find(_.isTimePeriod(closePrice.time)) match {
      case Some(candle) => candle.update(closePrice)
      case None =>
        if (marketCandles.nonEmpty) {
          val headCandle = marketCandles.head
          val normalizedTime = roundDateToMinute(closePrice.time, periodMinutes)
          // were candle periods skipped?
          val skippedCandles = ((normalizedTime.toEpochSecond - headCandle.time.toEpochSecond) / (periodMinutes * 60L)).toInt - 1
          for (i <- 1 to skippedCandles) {
            // add appropriate 5 minute interval to compute next candle time
            val time = headCandle.time.plusMinutes(periodMinutes * i)
            new MarketCandle(ClosePrice(time, headCandle.close), periodMinutes) +=: marketCandles
          }
        }

        // insert new candle at head
        new MarketCandle(closePrice, periodMinutes) +=: marketCandles

        // limit candle buffer length to 24 hours
        if (marketCandles.length > 288) {
          val removeNum = marketCandles.length - 288
          marketCandles.remove(288, removeNum)
        }
    }
  }
}
