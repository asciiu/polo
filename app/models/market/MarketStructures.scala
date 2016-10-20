package models.market

import java.time.OffsetDateTime

/**
  * Created by bishop on 10/18/16.
  */
object MarketStructures {
  case class ExponentialMovingAverage(time: OffsetDateTime, ema: BigDecimal, atPrice: BigDecimal)
  case class Candles(marketName: String, candles: List[MarketCandle])
  case class ClosePrice(time: OffsetDateTime, price: BigDecimal)

  case class CandleClosePrices(marketName: String, closePrices: List[ClosePrice])
  case class EMA(time: OffsetDateTime, ema: BigDecimal)
  case class PeriodVolume(time: OffsetDateTime, btcVolume: BigDecimal)

  case class MarketMessage(time: OffsetDateTime,
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
}
