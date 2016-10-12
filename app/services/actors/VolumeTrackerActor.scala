package services.actors

import java.time.OffsetDateTime
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import models.market.VolumeMatrix
import models.poloniex.PoloniexEventBus
import play.api.Configuration

// internals
import models.poloniex.MarketUpdate


object VolumeTrackerActor {
  trait VolumeTrackerMessage

  case class GetVolume(marketName: String, time: OffsetDateTime) extends VolumeTrackerMessage
  case class GetVolumes(marketName: String) extends VolumeTrackerMessage
  case class MarketVolume(marketName: String, btc24HrVolume: BigDecimal) extends VolumeTrackerMessage
}

/**
  * This Actor shall be responsible for tracking traded volume within periods.
  */
class VolumeTrackerActor @Inject() (configuration: Configuration) extends Actor with ActorLogging {
  import VolumeTrackerActor._

  val eventBus = PoloniexEventBus()
  val volumeMatrix = new VolumeMatrix()

  override def preStart() = {
    log info "started"
    eventBus.subscribe(self, "/market/update")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/update")
  }

  def receive = {

    case GetVolume(marketName, time) =>
      sender ! volumeMatrix.getVolume(marketName, time)

    case GetVolumes(marketName) =>
      sender ! volumeMatrix.getVolumes(marketName)

    case MarketUpdate(marketName, info) =>
      volumeMatrix.updateVolume(marketName, info.baseVolume)
  }
}
