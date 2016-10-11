package utils

import java.time.temporal.ChronoUnit
import java.time.{OffsetDateTime, ZoneOffset}

object Misc {

  def currentTimeRoundedDown(minutes: Int) = {
    roundDateToMinute(now(), minutes)
  }

  def now() = OffsetDateTime.now(ZoneOffset.UTC)

  def roundDateToMinute(dateTime: OffsetDateTime, minutes: Int): OffsetDateTime = {
    if (minutes < 1 || 5 % minutes != 0) {
      throw new IllegalArgumentException("minutes must be a factor of 5")
    }

    val m = dateTime.getMinute() / minutes
    dateTime.withMinute(minutes* m).truncatedTo(ChronoUnit.MINUTES)
  }
}
