package models.analytics.individual

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketStructures.BollingerBandPoint
import utils.Misc

import scala.collection.mutable.ListBuffer

// internal
import models.market.MarketEMACollection
import models.market.MarketStructures.{Candles, ClosePrice, MarketMessage}

/**
  * Implements bollinger bands
  */
trait Bollinger extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      val cp = ClosePrice(msg.time, msg.last)
      updateBands(cp)
      Inner(msg)

    /**
      * Assumes that the candles are orderd by latest time first!
      */
    case mc: Candles =>
      val cp = mc.candles.map( c => ClosePrice(c.time, c.close))
      closePrices ++= cp
      computeBands(cp)
      Inner(mc)
  }

  private val zeBands = ListBuffer[BollingerBandPoint]()

  // these are the system defaults but can be overridden
  val periodInAverage = 10
  val closePrices = ListBuffer[ClosePrice]()
  val periodMinutes = 5
  val percent: BigDecimal = 0.04
  // to be defined
  val marketName: String

  /**
    * Sets all averages for each period as a collection of exponential
    * moving averages.
    *
    * @param closePrices assumes the close prices are sorted with most recent
    *                    close period first and that all close prices have the same periodMinutes
    *                    as this object. Example, close prices for every 5 minute
    *                    period for the last 24 hour window.
    * @return
    */
  def computeBands(closePrices: List[ClosePrice]) = {
    // create a new collection for specific periods
    val periodPrices = closePrices.sliding(periodInAverage).toList

    for (prices <- periodPrices) {
      val mean = prices.map(_.price).sum / periodInAverage
      val sum = prices.map( p => (p.price - mean) * (p.price - mean)).sum
      val mean2 = sum / prices.length
      val std = Math.sqrt(mean2.toDouble)

      val upper = mean + 2 * std
      val lower = mean - 2 * std
      val time = prices.head.time

      zeBands.append(BollingerBandPoint(time, mean, upper, lower))
    }
  }

  private def updateBands(price: ClosePrice) = {
    if (closePrices.length > 0) {
      val normalTime = Misc.roundDateToMinute(price.time, periodMinutes)
      if (closePrices.head.time.equals(normalTime)) {
        closePrices.update(0, price)
      } else {
        closePrices.append(price)
      }

      val prices = closePrices.take(periodInAverage)
      val mean = prices.map(_.price).sum / periodInAverage
      val sumSqr = prices.map( p => (p.price - mean) * (p.price - mean)).sum
      val mean2 = sumSqr / prices.length
      val std = Math.sqrt(mean2.toDouble)

      val upper = mean + 2 * std
      val lower = mean - 2 * std
      val time = prices.head.time
      val band = BollingerBandPoint(time, mean, upper, lower)

      if (closePrices.head.time.equals(normalTime)) {
        zeBands.update(0, band)
      } else {
        zeBands.insert(0, band)
      }
    }
  }

  /**
    * Returns the latest ema for each period.
    *
    * @return map of period to ema for that period
    */
  def getLatestPoints(): Option[BollingerBandPoint] = {
    zeBands.headOption
  }

  /**
    * Returns the moving averages for this market.
    *
    * @return a map of period to exponential moving averages for that period
    *         list of ExponentialMovingAverages are ordered with most recent first
    */
  def getAllPoints(): List[BollingerBandPoint] = {
    zeBands.toList
  }
}
