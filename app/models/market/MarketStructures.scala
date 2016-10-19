package models.market

import java.time.OffsetDateTime

/**
  * Created by bishop on 10/18/16.
  */
object MarketStructures {
  case class ExponentialMovingAverage(time: OffsetDateTime, ema: BigDecimal, atPrice: BigDecimal)
  case class Candles(marketName: String, candles: List[MarketCandle])
  case class ClosePrice(time: OffsetDateTime, price: BigDecimal)
  case class EMA(time: OffsetDateTime, ema: BigDecimal)
  case class PeriodVolume(time: OffsetDateTime, btcVolume: BigDecimal)
}
