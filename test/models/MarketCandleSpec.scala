package models

import java.time.OffsetDateTime
import models.market.MarketCandle
import org.scalatest.FlatSpec
import utils.Misc


class MarketCandleSpec extends FlatSpec {
  import Misc._

  it should "create MarketCandle object from close price" in {
    val periodMinute = 5
    val price = BigDecimal(0.01)
    val time = now()
    val closePrice = ClosePrice(now(), price)
    val normalizedTime = roundDateToMinute(time, periodMinute)

    val candle = new MarketCandle(closePrice, 5)

    assert (candle.timePeriodMinutes === periodMinute)
    assert (candle.open === price)
    assert (candle.close === price)
    assert (candle.high === price)
    assert (candle.low === price)
    assert (candle.time.isEqual(normalizedTime))
  }

  it should "create MarketCandle object from low, high, etc" in {
    val periodMinute = 5
    val price = BigDecimal(0.01)
    val open = price
    val close = price + 0.000044
    val high = price + 0.00033
    val low = price - 0.0001

    val time = now()
    val normalizedTime = roundDateToMinute(time, periodMinute)

    val candle = new MarketCandle(time, periodMinute, open, close, high, low)

    assert (candle.timePeriodMinutes === periodMinute)
    assert (candle.open === open)
    assert (candle.close === close)
    assert (candle.high === high)
    assert (candle.low === low)
    assert (candle.time.isEqual(normalizedTime))

  }

  it should "update using the += operator with same time periods" in {
    val periodMinute = 5
    val price = BigDecimal(0.01)
    val open = price
    val close = price + 0.000044
    val high = price + 0.00033
    val higher = high + 1
    val low = price - 0.0001
    val lower = low - 0.00001

    val time1 = OffsetDateTime.parse("2016-10-19T02:26:15.110Z")
    val time2 = time1.plusSeconds(5)
    val normalizedTime = roundDateToMinute(time1, periodMinute)

    val candle1 = new MarketCandle(time1, periodMinute, open, close, high, low)
    val candle2 = new MarketCandle(time2, periodMinute, open, close, higher, lower)

    candle1 += candle2
    assert (candle1.open == open)
    assert (candle1.close == close)
    assert (candle1.high == candle2.high)
    assert (candle1.low == candle2.low)
    assert (candle2.high == higher)
    assert (candle2.low == lower)
  }

  it should "not update using the += operator when the time periods are different" in {
    val periodMinute = 5
    val price = BigDecimal(0.01)
    val open = price
    val close = price + 0.000044
    val high = price + 0.00033
    val higher = high + 1
    val low = price - 0.0001
    val lower = low - 0.00001
    val time1 = OffsetDateTime.parse("2016-10-19T02:26:55.110Z")
    val time2 = time1.plusMinutes(periodMinute)
    val normalizedTime = roundDateToMinute(time1, periodMinute)

    val candle1 = new MarketCandle(time1, periodMinute, open, close, high, low)
    val candle2 = new MarketCandle(time2, periodMinute, open, close, higher, lower)

    candle1 += candle2
    assert (candle1.open == open)
    assert (candle1.close == close)
    assert (candle1.high == high)
    assert (candle1.low == low)
    assert (candle2.high == higher)
    assert (candle2.low == lower)
  }

  it should "return true if same time period" in {
    val time = now()

    val candle = new MarketCandle(time, 5)

    assert(candle.isTimePeriod(time))
  }

  it should "update when the close price time is the same" in {
    val periodMinute = 5
    val price = BigDecimal(0.01)
    val open = price
    val close = price + 0.000044
    val high = price + 0.00033
    val low = price - 0.0001
    val time1 = OffsetDateTime.parse("2016-10-19T02:26:55.110Z")

    val candle = new MarketCandle(time1, periodMinute, open, close, high, low)
    val newLow = low - 0.000005748
    candle.update(ClosePrice(time1.plusSeconds(45), newLow))

    assert (candle.low == newLow)
    assert (candle.high == high)
    assert (candle.close == newLow)

    val newHigh = high + 0.000005748
    candle.update(ClosePrice(time1.plusSeconds(120), newHigh))

    assert (candle.low == newLow)
    assert (candle.high == newHigh)
    assert (candle.close == newHigh)
  }
}
