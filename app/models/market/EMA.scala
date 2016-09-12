package models.market

import org.joda.time.DateTime

/**
  * Created by bishop on 9/6/16.
  */
// TODO add period number
case class EMA(time: DateTime, ema: BigDecimal)
