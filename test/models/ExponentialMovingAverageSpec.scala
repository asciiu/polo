package models

// external
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.math.BigDecimal.RoundingMode

// internal
import models.market.{ExponentialMovingAverages, MarketExponentialMovingAvgs}
import utils.Misc


class ExponentialMovingAverageSpec extends FlatSpec with ScalaFutures with BeforeAndAfter {

  private def multiplier(periodNum: Int) : BigDecimal = {
    BigDecimal(2.0 / (periodNum + 1))
  }
  private def ema(close: BigDecimal, previousEMA: BigDecimal, period: Int): BigDecimal = {
    (close - previousEMA) * multiplier(period) + previousEMA
  }


  "MarketExponentialMovingAvgs" should "must be valid after init" in {
    val minutes = 5
    val period = 7
    val time = Misc.currentTimeRoundedDown(minutes)
    val prices = (for(i <- 0 until period) yield ClosePrice(time.minusMinutes(minutes * i), BigDecimal(i)))
    val sum = prices.foldLeft(BigDecimal(0.0))( (a, v) => a + v.price )
    val avg = sum / period

    val sevenPeriodAverages = new MarketExponentialMovingAvgs("Test", period, minutes, prices.toList)
    val average = sevenPeriodAverages.movingAverages.head

    assert(average.time == prices.head.time)
    assert(average.ema == avg)
  }

  "MarketExponentialMovingAvgs" should "init correctly when prices count is greater than period" in {

    val minutes = 5
    val period = 7
    val time = Misc.currentTimeRoundedDown(minutes)
    val prices = (for(i <- 0 to period) yield ClosePrice(time.minusMinutes(minutes * i), BigDecimal(i)))
    val sum = prices.foldLeft(BigDecimal(0.0))( (a, v) => a + v.price )

    val sevenPeriodAverages = new MarketExponentialMovingAvgs("Test", period, minutes, prices.toList)
    val averages = sevenPeriodAverages.movingAverages
    val avg = ema(prices.head.price, averages(1).ema, period)

    assert (averages.length == 2)
    assert (avg == averages.head.ema)
    assert (prices.head.time == averages.head.time)
  }

  "MarketExponentialMovingAvgs" must "update correctly" in {

    val minutes = 5
    val period = 7
    val time = Misc.currentTimeRoundedDown(minutes)
    val prices = (for(i <- 0 to period) yield ClosePrice(time.minusMinutes(minutes * i), BigDecimal(i)))
    val sum = prices.foldLeft(BigDecimal(0.0))( (a, v) => a + v.price )

    val sevenPeriodAverages = new MarketExponentialMovingAvgs("Test", period, minutes, prices.toList)
    for (i <- 1 to 5) {
      val t = Misc.now().plusSeconds(i)
      sevenPeriodAverages.updateAverages(ClosePrice(t, i))
    }

    val averages = sevenPeriodAverages.movingAverages
    val avg = ema(prices.head.price, averages(1).ema, period)

    assert (averages.length <= 3)
    assert (avg < averages.head.ema)
    assert (prices.head.time == averages.head.time)
  }

  "MarketExponentialMovingAvgs" should "add an average" in {

    val minutes = 5
    val period = 7
    val time = Misc.currentTimeRoundedDown(minutes)
    val prices = (for(i <- 0 to period) yield ClosePrice(time.minusMinutes(minutes * i), BigDecimal(i)))
    val sum = prices.foldLeft(BigDecimal(0.0))( (a, v) => a + v.price )

    val sevenPeriodAverages = new MarketExponentialMovingAvgs("Test", period, minutes, prices.toList)
    val t = prices.head.time.plusMinutes(minutes+1)
    val price = 1
    sevenPeriodAverages.updateAverages(ClosePrice(t, price))

    val averages = sevenPeriodAverages.movingAverages
    val avg = ema(price, averages(0).ema, period)

    assert (averages.length == 3)
    assert (avg < averages.head.ema)
    assert (averages.head.time == prices.head.time.plusMinutes(minutes))
  }

  "MarketExponentialMovingAvgs" should "add skipped periods" in {
    val minutes = 5
    val period = 5
    val skipped = 20
    val time = Misc.currentTimeRoundedDown(minutes)
    val prices = (for(i <- 0 to period) yield ClosePrice(time.minusMinutes(minutes * i), BigDecimal(i)))
    val lastPrice = prices.head.price
    val sum = prices.foldLeft(BigDecimal(0.0))( (a, v) => a + v.price )

    val sevenPeriodAverages = new MarketExponentialMovingAvgs("Test", period, minutes, prices.toList)
    val averages = sevenPeriodAverages.movingAverages

    val t = prices.head.time.plusMinutes(minutes * skipped + 3)
    val normalizedTime = Misc.roundDateToMinute(t, minutes)
    var lastEMA = averages.head.ema

    for (i <- 0 until skipped - 1) {
      lastEMA = ema(lastPrice, lastEMA, period).setScale(8, RoundingMode.CEILING)
    }
    val price = 1
    lastEMA = ema(price, lastEMA, period).setScale(8, RoundingMode.CEILING)

    val before = averages.length
    sevenPeriodAverages.updateAverages(ClosePrice(t, price))

    assert ( lastEMA == averages.head.ema)
    assert (averages.length == skipped + before)
    assert (averages.head.time == normalizedTime)
  }
}