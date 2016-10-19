package models.poloniex

import java.time.OffsetDateTime
import utils.Misc

case class MarketUpdate(marketName: String, info: MarketMessage)

//"BTC_1CR":{"id":1,"last":"0.00185835","lowestAsk":"0.00195342","highestBid":"0.00185835","percentChange":"-0.02771891",
// "baseVolume":"6.80216315","quoteVolume":"3424.46300964","isFrozen":"0","high24hr":"0.00220238","low24hr":"0.00178000"}
case class MarketMessage(id: Int,
                         last: BigDecimal,
                         lowestAsk: BigDecimal,
                         highestBid: BigDecimal,
                         percentChange: BigDecimal,
                         baseVolume: BigDecimal,
                         quoteVolume: BigDecimal,
                         isFrozen: String,
                         high24hr: BigDecimal,
                         low24hr: BigDecimal)

// TODO get ride of above
case class MarketMessage2(time: OffsetDateTime,
                          cryptoCurrency: String,
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
case class PoloMarketCandle(date: OffsetDateTime,
                         high: BigDecimal,
                         low: BigDecimal,
                         open: BigDecimal,
                         close: BigDecimal,
                         volume: BigDecimal,
                         quoteVolume: BigDecimal,
                         weightedAverage: BigDecimal)

