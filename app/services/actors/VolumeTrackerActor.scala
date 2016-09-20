package services.actors

import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import models.poloniex.PoloniexEventBus
import org.joda.time.DateTime
import play.api.Configuration

import scala.collection.mutable.ListBuffer

// internals
import models.market.PeriodVolume
import models.poloniex.{MarketCandle, MarketUpdate}


object VolumeTrackerActor {
  trait VolumeTrackerMessage

  case class GetVolume(marketName: String, time: DateTime) extends VolumeTrackerMessage
  case class GetVolumes(marketName: String) extends VolumeTrackerMessage
  case class MarketVolume(marketName: String, btc24HrVolume: BigDecimal) extends VolumeTrackerMessage
}

/**
  * This Actor shall be responsible for tracking traded volume within periods.
  */
class VolumeTrackerActor @Inject() (configuration: Configuration) extends Actor with ActorLogging {
  import VolumeTrackerActor._

  val eventBus = PoloniexEventBus()

  val periodVolumes = scala.collection.mutable.Map[String,  ListBuffer[PeriodVolume]]()

  override def preStart() = {
    log info "started"
    //eventBus.subscribe(self, "/market/volumes")
    eventBus.subscribe(self, "/market/update")
  }

  override def postStop() = {
    //eventBus.unsubscribe(self, "/market/volumes")
    eventBus.unsubscribe(self, "/market/update")
  }

  def receive = {
    case GetVolume(marketName, time) =>
      periodVolumes.get(marketName) match {
        case Some(volumes) =>
          volumes.find(_.time.equals(time)) match {
            case Some(vol) => sender ! vol
            case None => sender ! PeriodVolume(time, 0)
          }
        case None =>
          sender ! PeriodVolume(time, 0)
      }

    case GetVolumes(marketName) =>
      periodVolumes.get(marketName) match {
        case Some(volumes) => sender ! volumes.toList
        case None => sender ! List[PeriodVolume]()
      }

    case MarketUpdate(marketName, info) =>
      updateVolume(marketName, info.baseVolume)
  }

  /**
    * Sets the period volumes for a market. In theory this should only need to be invoked once after
    * the candles are retrieved from the exchange.
    *
    * @param marketName
    * @param volumes - sorted in descending date MUST be 24 hours
    */
  private def setVolumes(marketName: String, volumes: List[PeriodVolume]) = {
    val newBuffer = scala.collection.mutable.ListBuffer.empty[PeriodVolume]

    if (volumes.length > 288) {
      newBuffer.appendAll(volumes.take(288))
    } else {
      newBuffer.appendAll(volumes)
    }
    periodVolumes.put(marketName, newBuffer)
  }

  /**
    * Updates the last market volume period based upon the 24 hr volume in BTC. Note: This
    * update assumes the volumes are set for the past 24 hour periods.
    *
    * @param marketName
    * @param btc24HrVolume
    */
  private def updateVolume(marketName: String, btc24HrVolume: BigDecimal) = {
    val time = MarketCandle.roundDateToMinute(new DateTime(), 5)

    periodVolumes.get(marketName) match {
      case Some(volumes) =>
        val time = MarketCandle.roundDateToMinute(new DateTime(), 5)
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
