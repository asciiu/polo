package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import java.time.OffsetDateTime
import scala.collection.mutable.ListBuffer

// internal
import models.market.MarketStructures.PeriodVolume
import models.market.MarketStructures.MarketMessage
import utils.Misc

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

    periodVolumes.get(marketName) match {
      case Some(volumes) =>
        val lastVolume = volumes.head

        // update the latest volume
        if (lastVolume.time.equals(time)) {
          volumes.remove(0)
          volumes.insert(0, PeriodVolume(time, btc24HrVolume))
        } else {
          volumes.insert(0, PeriodVolume(time, btc24HrVolume))

          // limit volumes to 24 hours
          if (volumes.length > 288) {
            val removeNum = volumes.length - 288
            volumes.remove(288, removeNum)
          }
        }
      case None =>
        val newBuffer = scala.collection.mutable.ListBuffer.empty[PeriodVolume]
        newBuffer.append(PeriodVolume(time, btc24HrVolume))
        periodVolumes.put(marketName, newBuffer)
        log.debug("can't process new volume because the 24 hour volumes was not set")
    }
  }
}

