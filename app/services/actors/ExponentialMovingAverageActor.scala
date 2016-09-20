package services.actors

import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import TradeActor.MarketEMA
import org.joda.time.DateTime
import play.api.Configuration
import utils.poloniex.MarketEvent

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

// internals
import models.market.{ClosePrice, EMA}
import utils.poloniex.PoloniexEventBus


object ExponentialMovingAverageActor {
  trait EMAMessage

  case class MarketCandleClose(marketName: String, close: ClosePrice)
  case class MarketCandleClosePrices(marketName: String, prices: List[ClosePrice]) extends EMAMessage
  case class AddEMAPeriod(periods: Int) extends EMAMessage
  case class GetMovingAverage(marketname: String, time: DateTime) extends EMAMessage
  case class GetMovingAverages(marketName: String) extends EMAMessage
}

/**
  * This Actor shall be responsible for tracking moving averages for markets.
  */
class ExponentialMovingAverageActor @Inject() (configuration: Configuration) extends Actor with ActorLogging {
  import ExponentialMovingAverageActor._

  val eventBus = PoloniexEventBus()

  // maps (MarketName, NumberOfPeriods) -> List[EMA]
  // number of periods is the number of candles to compute average for: example 7 candles
  // the list of ema shall be order in descending date with more recent moving average at head
  val movingAverages = scala.collection.mutable.Map[(String, Int),  ListBuffer[EMA]]()

  // tracking periods pertains to ema periods that this actor is responsible for tracking
  val trackingPeriods = scala.collection.mutable.ListBuffer.empty[Int]

  override def preStart() = {
    log info "started"
    eventBus.subscribe(self, "/market/candle/close")
    eventBus.subscribe(self, "/market/prices")

    // default tracking periods are 7 and 15 candles
    trackingPeriods ++= List(5, 15)
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/candle/close")
    eventBus.unsubscribe(self, "/market/prices")
  }

  def receive = {
    case AddEMAPeriod(num) =>
      trackingPeriods.append(num)

    case GetMovingAverage(marketName, time) =>
      val avgs = for (periodNum <- trackingPeriods if (movingAverages.contains((marketName, periodNum)))) yield {
        // tuple of (periodNum, ema)
        (
          periodNum,
          movingAverages.get((marketName, periodNum)).get.find( a => a.time.equals(time)).getOrElse(EMA(time, 0)).ema
        )
      }
      sender ! avgs.toList


    /**
      * Send to original sender the moving averages of a market.
      */
    case GetMovingAverages(marketName) =>

      val allAvgs = for (periodNum <- trackingPeriods if (movingAverages.contains((marketName, periodNum)))) yield {
        (periodNum, movingAverages.get((marketName, periodNum)).get.toList)
      }
      sender ! allAvgs.toList

    case MarketCandleClose(marketName, close) =>
      val updates = for (periodNum <- trackingPeriods) yield {
        updateAverages(marketName, close, periodNum)
      }
      if (updates(0).ema != 0 && updates(1).ema != 0) {
        eventBus.publish(MarketEvent("/ema/update", MarketEMA(marketName, updates(0), updates(1))))
      }


    case MarketCandleClosePrices(marketName, prices) =>
      // for each tracking period compute the moving averages
      for (periodNum <- trackingPeriods) {
        // assumes prices are order by descending date time
        setAverages(marketName, prices, periodNum)
      }
  }

  /**
    * Multiplier used for weighting exponential moving average.
    *
    * @param periodNum
    * @return
    */
  private def multiplier(periodNum: Int) : BigDecimal = {
    BigDecimal(2.0 / (periodNum + 1))
  }

  /**
    * Sets the moving averages for market name based upon the received closing prices.
    * The moving average time is dictated by periodNum. This SHOULD only be invoked once
    * per market period number - i.e after retrieving the closing prices from poloniex.
    *
    * @param marketName
    * @param closingPrices - assumes closing prices list is ordered in descending date
    * @param periodNum - number of candles to compute moving average for
    */
  private def setAverages(marketName: String, closingPrices: List[ClosePrice], periodNum: Int) = {
    // we must have more closing prices than the period number in order to compute the simple
    // moving average
    if (closingPrices.length > periodNum) {
      val averages = scala.collection.mutable.ListBuffer.empty[EMA]

      val firstClose = closingPrices(closingPrices.length - periodNum)
      val sum = closingPrices.takeRight(periodNum).foldLeft(BigDecimal(0))( (a, p) => p.price + a)
      val simpleMovingAverage = sum / periodNum
      // exponential moving average begins with a simple moving average
      averages.append(EMA(firstClose.time, simpleMovingAverage))

      // loop through all closing prices from firstClose and compute exponential moving average
      for (i <- periodNum+1 until closingPrices.length) {
        val previousEMA = averages.head.ema

        val close = closingPrices(closingPrices.length - i)

        // formula = {Close - EMA(previous day)} x multiplier + EMA(previous day)
        // source: http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:moving_averages
        val ema = (close.price - previousEMA) * multiplier(periodNum) + previousEMA
        // most recent average always at head
        averages.insert(0, EMA(close.time, ema.setScale(8, RoundingMode.CEILING)))
      }

      movingAverages.put((marketName, periodNum), averages)
    }
  }

  /**
    * Updates the moving average for a market period number given a close price. This will most likely
    * be invoked when a new closing price is received. This actor assumes that the close price
    * received is the latest close price.
    *
    * @param marketName
    * @param closePrice - the latest close price
    * @param periodNum
    * @return
    */
  private def updateAverages(marketName: String, closePrice: ClosePrice, periodNum: Int): EMA = {
    movingAverages.get((marketName, periodNum)) match {
      case Some(averages) =>
        val latestAvg = averages.head

        val updateAvg = if (latestAvg.time.isEqual(closePrice.time)) {
          val previousEMA = averages(1).ema
          val ema = (closePrice.price - previousEMA) * multiplier(periodNum) + previousEMA
          // replace the head with new
          averages.remove(0)
          averages.insert(0, EMA(closePrice.time, ema.setScale(8, RoundingMode.CEILING)))
          EMA(closePrice.time, ema)
        } else {
          val previousEMA = averages(0).ema
          val ema = (closePrice.price - previousEMA) * multiplier(periodNum) + previousEMA
          // replace the head with new
          averages.insert(0, EMA(closePrice.time, ema.setScale(8, RoundingMode.CEILING)))
          EMA(closePrice.time, ema)
        }

        // limit averages to 24 hours
        if (averages.length > 288) {
          val removeNum = averages.length - 288
          averages.remove(288, removeNum)
        }
        updateAvg
      case None =>
        log.debug(s"can't update moving average for $marketName because I haven't received initial averages for this market")
        EMA(closePrice.time, 0)
    }
  }
}
