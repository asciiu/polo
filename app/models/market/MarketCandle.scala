package models.market

import java.time.OffsetDateTime

import utils.Misc
import MarketStructures.ClosePrice

/**
  * Created by bishop on 10/18/16.
  */
class MarketCandle (timestamp: OffsetDateTime,
                    val timePeriodMinutes: Int) {

  import Misc._

  val time = roundDateToMinute(timestamp, timePeriodMinutes)

  def this(closePrice: ClosePrice, periodMinutes: Int) {
    this(closePrice.time, periodMinutes)
    open = closePrice.price
    close = closePrice.price
    low = closePrice.price
    high = closePrice.price
  }

  def this(time: OffsetDateTime, periodMinutes: Int, open: BigDecimal,
           close: BigDecimal, high: BigDecimal, low: BigDecimal) {
    this(time, periodMinutes)
    this.open = open
    this.close = close
    this.high = high
    this.low = low
  }

  var open: BigDecimal = 0
  var low: BigDecimal = 0
  var high: BigDecimal = 0
  var close: BigDecimal = 0
  //var volumeBtc: BigDecimal = 0

  def isBuy(): Boolean = close > open

  def +=(candle: MarketCandle): MarketCandle = {
    if (isTimePeriod(candle.time)) {
      if (candle.low < low || low == 0) low = candle.low
      if (candle.high > high || high == 0) high = candle.high
    }
    this
  }

  /**
    * Assumes the close price time is normalized
    *
    * @param closePrice
    */
  def update(closePrice: ClosePrice) = {
    if (isTimePeriod(closePrice.time)) {
      if (closePrice.price < low || low == 0) low = closePrice.price
      if (closePrice.price > high || high == 0) high = closePrice.price
      close = closePrice.price
    }
  }

  def isTimePeriod(time: OffsetDateTime) : Boolean = {
    val normalizedTime = roundDateToMinute(time, timePeriodMinutes)
    this.time.isEqual(normalizedTime)
  }
}