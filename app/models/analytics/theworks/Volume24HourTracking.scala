package models.analytics.theworks

import java.time.OffsetDateTime

import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import models.market.MarketStructures.{MarketMessage, PeriodVolume}
import utils.Misc

import scala.collection.mutable.ListBuffer

/**
  * Provides DB archiving of messages and candles.
  */
trait Volume24HourTracking extends ActorLogging {

  this: ReceivePipeline => pipelineInner {

    case msg: MarketMessage =>
      updateVolume(msg.time, msg.cryptoCurrency, msg.baseVolume)
      Inner(msg)
  }

  val periodVolumes = scala.collection.mutable.Map[String,  ListBuffer[PeriodVolume]]()

  def getVolume(marketName: String, time: OffsetDateTime): PeriodVolume = {
    periodVolumes.get(marketName) match {
      case Some(volumes) =>
        volumes.find(_.time.equals(time)) match {
          case Some(vol) => vol
          case None => PeriodVolume(time, 0)
        }
      case None =>
        PeriodVolume(time, 0)
    }
  }

  def getVolumes(marketName: String): List[PeriodVolume] = {
    periodVolumes.get(marketName) match {
      case Some(volumes) => volumes.toList
      case None => List[PeriodVolume]()
    }
  }

  /**
    * Updates the last market volume period based upon the 24 hr volume in BTC. Note: This
    * update assumes the volumes are set for the past 24 hour periods.
    *
    * @param marketName
    * @param btc24HrVolume
    */
  def updateVolume(time: OffsetDateTime, marketName: String, btc24HrVolume: BigDecimal) = {

    val normalizedTime = Misc.roundDateToMinute(time, 5)

    periodVolumes.get(marketName) match {
      case Some(volumes) =>
        val lastVolume = volumes.head

        // update the latest volume
        if (lastVolume.time.equals(normalizedTime)) {
          volumes.update(0, PeriodVolume(normalizedTime, btc24HrVolume))
        } else {
          volumes.insert(0, PeriodVolume(normalizedTime, btc24HrVolume))

          // limit volumes to 24 hours
          if (volumes.length > 288) {
            val removeNum = volumes.length - 288
            volumes.remove(288, removeNum)
          }
        }
      case None =>
        val newBuffer = scala.collection.mutable.ListBuffer.empty[PeriodVolume]
        newBuffer.append(PeriodVolume(normalizedTime, btc24HrVolume))
        periodVolumes.put(marketName, newBuffer)
        log.debug("can't process new volume because the 24 hour volumes was not set")
    }
  }
}
