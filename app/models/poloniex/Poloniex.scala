package models.poloniex

import org.joda.time.DateTime


case class Market(ticker: String, status: MarketStatus)

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

//case class MarketCandle(ticker: String,
//                        time: DateTime,
//                        var low: BigDecimal,
//                        var high: BigDecimal,
//                        open: BigDecimal,
//                        var close: BigDecimal,
//                        volumeTotal: BigDecimal) {
//
//  def updateInfo(update: PoloniexMarketUpdate): Unit = {
//    // tickers must match
//    if (update.ticker == ticker) {
//      // is the last greater than high
//      if (high < update.last) high = update.last
//      if (low > update.last) low = update.last
//    }
//  }
//}

