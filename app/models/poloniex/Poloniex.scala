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

//case class PoloniexMarketUpdate(ticker: String,
//                        last: BigDecimal,
//                        lowestAsk: BigDecimal,
//                        highestBid: BigDecimal,
//                        percentChange: BigDecimal,
//                        baseVolume: BigDecimal,
//                        quoteVolume: BigDecimal,
//                        isFrozen: Boolean,
//                        high24hr: BigDecimal,
//                        low24hr: BigDecimal)

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
}

case class MarketCandle(time: DateTime,
                        timeIntervalMinutes: Int,
                        open: BigDecimal) {

  var low: BigDecimal = 0
  var high: BigDecimal = 0
  var close: BigDecimal = 0
  var volumeBtc24Hr: BigDecimal = 0

  def isBuy(): Boolean = {
    close-open > 0
  }

  def updateInfo(update: MarketStatus): Unit = {
    if (update.last < low || low == 0) low = update.last
    if (update.last > high || high == 0) high = update.last

    // CryptoGuppy: troII, say you have the currency pair XMR_DASH,
    // then the basevolume is vol. in XMR, quotevolume is vol. in DASH
    volumeBtc24Hr = update.baseVolume

    close = update.last
  }

  def isTimeInterval(t: DateTime): Boolean = {
    val normalTime = MarketCandle.roundDateToMinute(t, timeIntervalMinutes)
    if (time.isEqual(normalTime)) true
    else false
  }
}

