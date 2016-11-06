package models.analytics.individual

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketStructures.BollingerBandPoint

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
      averages.foreach(_.updateAverages(ClosePrice(msg.time, msg.last)))
      Inner(msg)

    /**
      * Assumes that the candles are orderd by latest time first!
      */
    case mc: Candles =>
      val cp = mc.candles.map( c => ClosePrice(c.time, c.close))
      closePrices ++= cp
      computeCenterAverages(cp)
      Inner(mc)
  }

  private val upperband = ListBuffer[BigDecimal]()

  // these are the system defaults but can be overridden
  val centerAverageperiods: List[Int] = List(10)
  val closePrices = ListBuffer[ClosePrice]()
  val periodMinutes = 5
  val percent: BigDecimal = 0.04
  // to be defined
  val marketName: String

  // list of ema collections
  private val averages = scala.collection.mutable.ListBuffer[MarketEMACollection]()

  def setAllCenterAverages(marketAverages: List[MarketEMACollection]) = averages ++= marketAverages

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
  def computeCenterAverages(closePrices: List[ClosePrice]) = {
    // create a new collection for specific periods
    val newAverages = for (period <- centerAverageperiods)
      yield new MarketEMACollection(marketName, period, periodMinutes, closePrices)

    averages ++= newAverages
  }

  /**
    * Returns the latest ema for each period.
    *
    * @return map of period to ema for that period
    */
  def getLatestPoints(): Option[BollingerBandPoint] = {
    if (averages.nonEmpty) {
      val avg = averages.head.emas.head
      Some(BollingerBandPoint(avg.time, avg.ema, avg.ema * (1+percent), avg.ema * (1-percent)))
    } else {
      None
    }
  }

  /**
    * Returns the moving averages for this market.
    *
    * @return a map of period to exponential moving averages for that period
    *         list of ExponentialMovingAverages are ordered with most recent first
    */
  def getAllPoints(): List[BollingerBandPoint] = {
    if (averages.nonEmpty) {
      val emas = averages.head.emas
      emas.map { avg =>
        val center = avg.ema
        val time1 = avg.time.minusMinutes(periodMinutes * (centerAverageperiods.head+1))
        val prices = closePrices.filter{ p =>
          p.time.isAfter(time1) && p.time.isBefore(avg.time.plusMinutes(periodMinutes))
        }

        // based on formula
        // Upper Band = Period avg + (10-day standard deviation of price x 2)
        // Lower Band = Period avg - (10-day standard deviation of price x 2)
        val mean = prices.map(_.price).sum / prices.length
        val sum = prices.map( p => (p.price - mean) * (p.price - mean)).sum
        val mean2 = sum / prices.length
        val std = Math.sqrt(mean2.toDouble)

        val upper = center + 2 * std
        val lower = center - 2 * std
        BollingerBandPoint(avg.time, center, upper, lower)
      }
    } else {
      List.empty[BollingerBandPoint]
    }
  }
}
