package utils.poloniex

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.poloniex.Market
import play.api.Configuration

import scala.language.postfixOps

object PoloniexWebSocketSupervisor {
  def props(url: String)(implicit system: ActorSystem): Props = Props(new PoloniexWebSocketSupervisor(url))
}

class PoloniexWebSocketSupervisor(url: String)(implicit system: ActorSystem) extends Actor with ActorLogging {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  val eventBus = PoloniexEventBus()

  override def preStart() = {
    log info "started the supervisor"
    context.actorOf(PoloniexWebSocketClient.props(url))
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => {
        log debug s"$e encountered restart"
        Restart
    }
  }

  def receive: Receive = {
    case update: Market => eventBus.publish(MarketEvent("/market/update", update))
  }
}
