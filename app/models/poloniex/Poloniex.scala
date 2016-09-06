package models.poloniex

import org.joda.time.DateTime


case class Market(name: String, status: MarketStatus)

//"BTC_1CR":{"id":1,"last":"0.00185835","lowestAsk":"0.00195342","highestBid":"0.00185835","percentChange":"-0.02771891",
// "baseVolume":"6.80216315","quoteVolume":"3424.46300964","isFrozen":"0","high24hr":"0.00220238","low24hr":"0.00178000"}
case class MarketStatus(id: Int,
                        last: BigDecimal,
                        lowestAsk: BigDecimal,
                        highestBid: BigDecimal,
                        percentChange: BigDecimal,
                        baseVolume: BigDecimal,
                        quoteVolume: BigDecimal,
                        isFrozen: String,
                        high24hr: BigDecimal,
                        low24hr: BigDecimal)

//[{"date":1405699200,"high":0.0045388,"low":0.00403001,"open":0.00404545,"close":0.00427592,"volume":44.11655644,
//"quoteVolume":10259.29079097,"weightedAverage":0.00430015}, ...]
case class PoloMarketCandle(date: DateTime,
                         high: BigDecimal,
                         low: BigDecimal,
                         open: BigDecimal,
                         close: BigDecimal,
                         volume: BigDecimal,
                         quoteVolume: BigDecimal,
                         weightedAverage: BigDecimal)


object MarketCandle {
  def roundDateToMinute(dateTime: DateTime, minutes: Int): DateTime = {
    if (minutes < 1 || 5 % minutes != 0) {
      throw new IllegalArgumentException("minutes must be a factor of 5")
    }

    val m = dateTime.getMinuteOfHour() / minutes
    new DateTime(dateTime.getYear(),
      dateTime.getMonthOfYear(),
      dateTime.getDayOfMonth,
      dateTime.getHourOfDay(),
      m * minutes
    )
  }

  def apply (poloCandle: PoloMarketCandle): MarketCandle = {
    val candle = MarketCandle(poloCandle.date, 5, poloCandle.open)
    candle.low = poloCandle.low
    candle.high = poloCandle.high
    candle.volumeBtc = poloCandle.volume
    candle.close = poloCandle.close
    candle
  }
}

case class MarketCandle(time: DateTime,
                        timeIntervalMinutes: Int,
                        var open: BigDecimal) {
  var low: BigDecimal = 0
  var high: BigDecimal = 0
  var close: BigDecimal = 0
  var volumeBtc: BigDecimal = 0

  def isBuy(): Boolean = {
    (close - open) > 0
  }

  def addCandle(candle: MarketCandle): Unit = {
    // TODO this needs to know if the open time is less
    open = candle.open
    if (candle.low < low) low = candle.low
    if (candle.high > high) high = candle.high
  }

  def updateStatus(update: MarketStatus): Unit = {
    if (update.last < low || low == 0) low = update.last
    if (update.last > high || high == 0) high = update.last
    close = update.last
  }
}

