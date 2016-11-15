package services.actors

// external
import akka.actor.{Actor, ActorLogging, Props}
import javax.inject.Inject
import scala.collection.mutable
import scala.concurrent.ExecutionContext

// internal
import models.market.MarketStructures.MarketSetupNotification
import models.poloniex.PoloniexEventBus


object NotificationService{
  def props()(implicit context: ExecutionContext) =
    Props(new NotificationService)

  case object GetMarketSetup
}

class NotificationService @Inject()(implicit ctx: ExecutionContext)
  extends Actor with ActorLogging {

  import NotificationService._

  val eventBus = PoloniexEventBus()
  val goodMarkets = mutable.Set[String]()

  override def preStart() = {
    eventBus.subscribe(self, PoloniexEventBus.BollingerNotification)
  }

  override def postStop() = {
    eventBus.unsubscribe(self, PoloniexEventBus.BollingerNotification)
  }

  def receive = {
    case MarketSetupNotification(marketName, isSetup) if (isSetup) =>
      log.info(s"MarketSetup: $marketName true")
      goodMarkets += marketName

    case MarketSetupNotification(marketName, isSetup) if (!isSetup) =>
      log.info(s"MarketSetup: $marketName false")
      goodMarkets -= marketName

    case GetMarketSetup =>
      sender ! goodMarkets.toSet
  }
}

