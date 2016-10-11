import java.time.{OffsetDateTime, ZoneOffset}

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import utils.Misc

class UtilsSpec extends FlatSpec with ScalaFutures {

  "Rounding the current down" should "round down 5 minutes" in {
    val correct = OffsetDateTime.parse("2016-10-11T22:10Z")
    val dateTime = OffsetDateTime.parse("2016-10-11T22:14:36.317Z")
    val roundedTime = Misc.roundDateToMinute(dateTime, 5)

    assert(roundedTime.equals(correct))
  }
}
