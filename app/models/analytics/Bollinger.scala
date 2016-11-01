package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner


// internal
import models.market.MarketEMACollection
import models.market.MarketStructures.{Candles, ClosePrice, ExponentialMovingAverage, MarketMessage}

//trait Bollinger extends ActorLogging {
//
//  this: ReceivePipeline => pipelineInner {
//    case msg: MarketMessage =>
//      val marketName = msg.cryptoCurrency
//      val currentPrice = msg.last
//      if (averages.contains(marketName)) {
//        averages(marketName).updateAverages(ClosePrice(msg.time, currentPrice))
//      }
//      Inner(msg)
//
//    /**
//      * Assumes that the candles are orderd by latest time first!
//      */
//    case mc: Candles =>
//      val closePrices = mc.candles.map( c => ClosePrice(c.time, c.close))
//      setAverages(mc.marketName, closePrices)
//      Inner(mc)
//  }
//
//  // these are the system defaults but can be overridden
//  val emaPeriods = 10
//  val periodMinutes = 5
//
//  // map of marketName -> list of ema collections
//  private val averages = scala.collection.mutable.Map[String, MarketEMACollection]()
//
//  case class BollingerPoint(deviation: Double, fromCenter: BigDecimal)
//  private val upperband = scala.collection.mutable.Map[String, List[BollingerPoint]]
//
//  def allAverages = averages.toMap
//
//  def setAllMarketAverages(marketAverages: Map[String, MarketEMACollection]) = averages ++= marketAverages
//
//  /**
//    * Sets all averages for each period as a collection of exponential
//    * moving averages.
//    *
//    * @param marketName
//    * @param closePrices assumes the close prices are sorted with most recent
//    *                    close period first and that all close prices have the same periodMinutes
//    *                    as this object. Example, close prices for every 5 minute
//    *                    period for the last 24 hour window.
//    * @return
//    */
//  def setAverages(marketName: String, closePrices: List[ClosePrice]) = {
//    // create a new collection for specific periods
//    val avgs = new MarketEMACollection(marketName, emaPeriods, periodMinutes, closePrices)
//
//    averages.put(marketName, avgs)
//  }
//
//  /**
//    * returns the average
//    */
//  def getLatestCenter(marketName: String): BigDecimal = {
//    averages.get(marketName) match {
//      case Some(avgs) => avgs.emas.head.ema
//      case None => 0.0
//    }
//  }
//
//  /**
//    * Send to original sender the moving averages of a market.
//    * returns a List[(Int, List[ExponentialMovingAverage])]
//    */
//  def getMovingAverages(marketName: String) = List[ExponentialMovingAverage] = {
//    averages.get(marketName) match {
//      case Some(avgs) => avgs.emas
//      case None => List[ExponentialMovingAverage]()
//    }
//  }
//}
