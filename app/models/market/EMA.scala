package models.market

import java.time.OffsetDateTime

/**
  * Created by bishop on 9/6/16.
  */
// TODO add period number
case class EMA(time: OffsetDateTime, ema: BigDecimal)
