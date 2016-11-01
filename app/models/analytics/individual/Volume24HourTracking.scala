package models.analytics.individual

import java.time.OffsetDateTime

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import scala.collection.mutable.ListBuffer
import models.market.MarketStructures.{MarketMessage, PeriodVolume}
import utils.Misc


trait Volume24HourTracking extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>
      updateVolume(msg.time, msg.cryptoCurrency, msg.baseVolume)
      Inner(msg)
  }

  val periodVolumes = ListBuffer[PeriodVolume]()

  def getVolume(time: OffsetDateTime): PeriodVolume = {
    periodVolumes.find(_.time.equals(time)) match {
      case Some(vol) => vol
      case None => PeriodVolume(time, 0)
    }
  }

  def getVolumes(): List[PeriodVolume] = {
    periodVolumes.toList
  }

  /**
    * Updates the last market volume period based upon the 24 hr volume in BTC. Note: This
    * update assumes the volumes are set for the past 24 hour periods.
    *
    * @param btc24HrVolume
    */
  def updateVolume(time: OffsetDateTime, marketName: String, btc24HrVolume: BigDecimal) = {

    val normalizedTime = Misc.roundDateToMinute(time, 5)

    if (periodVolumes.nonEmpty) {
      val lastVolume = periodVolumes.head

      // update the latest volume
      if (lastVolume.time.equals(normalizedTime)) {
        periodVolumes.update(0, PeriodVolume(normalizedTime, btc24HrVolume))
      } else {
        periodVolumes.insert(0, PeriodVolume(normalizedTime, btc24HrVolume))
      }

      // limit volumes to 24 hours
      if (periodVolumes.length > 288) {
        val removeNum = periodVolumes.length - 288
        periodVolumes.remove(288, removeNum)
      }
    } else {
      periodVolumes.append(PeriodVolume(normalizedTime, btc24HrVolume))
    }
  }
}
