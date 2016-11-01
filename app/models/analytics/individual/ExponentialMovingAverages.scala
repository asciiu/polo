package models.analytics.individual

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketEMACollection
import models.market.MarketStructures.{Candles, ClosePrice, ExponentialMovingAverage, MarketMessage}

/**
  * Created by bishop on 11/1/16.
  */
trait ExponentialMovingAverages extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      averages.foreach(_.updateAverages(ClosePrice(msg.time, msg.last)))
      Inner(msg)

    /**
      * Assumes that the candles are orderd by latest time first!
      */
    case mc: Candles =>
      val closePrices = mc.candles.map( c => ClosePrice(c.time, c.close))
      setAverages(closePrices)
      Inner(mc)
  }

  // to be defined
  val marketName: String

  // these are the system defaults but can be overridden
  val periods: List[Int] = List(7, 15)
  val periodMinutes = 5

  // list of ema collections
  private val averages = scala.collection.mutable.ListBuffer[MarketEMACollection]()

  def setAllMarketAverages(marketAverages: List[MarketEMACollection]) = averages ++= marketAverages

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
  def setAverages(closePrices: List[ClosePrice]) = {
    // create a new collection for specific periods
    val newAverages = for (period <- periods)
      yield new MarketEMACollection(marketName, period, periodMinutes, closePrices)

    averages ++= newAverages
  }

  /**
    * Returns the latest ema for each period.
    *
    * @return map of period to ema for that period
    */
  def getLatestMovingAverages(): Map[Int, BigDecimal] = {
    if (averages.nonEmpty) {
      averages.map(a => a.period -> a.emas.head.ema).toMap
    } else {
      Map[Int, BigDecimal]()
    }
  }

  /**
    * Returns the moving averages for this market.
    *
    * @return a map of period to exponential moving averages for that period
    */
  def getMovingAverages(): Map[Int, List[ExponentialMovingAverage]] = {
    if (averages.nonEmpty) {
      averages.map(a => a.period -> a.emas).toMap
    } else {
      Map.empty[Int, List[ExponentialMovingAverage]]
    }
  }
}
