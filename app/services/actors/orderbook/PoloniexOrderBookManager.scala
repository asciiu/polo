package services.actors.orderbook

// external
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.actor.PoisonPill
import javax.inject.Inject

import play.api.Configuration
import play.api.libs.ws.WSClient
import services.actors.orderbook.PoloniexOrderBookSubscriber.DoDisconnect

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

// internal
import models.poloniex.PoloniexEventBus
import services.DBService


object PoloniexOrderBookManager {

  case class Subscribe(marketName: String)
  case class Unsubscribe(marketName: String)
}

/**
  * This actor is reponsible for managing all poloniex markets. New
  * actors for each market are created here.
  */
class PoloniexOrderBookManager @Inject()(val database: DBService,
                                         ws: WSClient,
                                         conf: Configuration)(implicit ctx: ExecutionContext)
  extends Actor
  with ActorLogging {

  import PoloniexOrderBookManager._

  // keep tabs on each market ref by market name
  val markets = scala.collection.mutable.Map[String, ActorRef]()
  val subscriberCounts = scala.collection.mutable.Map[String, Int]()

  val eventBus = PoloniexEventBus()

  override def preStart() = {
    eventBus.subscribe(self, PoloniexEventBus.OrderBookSubscribers)
  }

  override def postStop() = {
    eventBus.unsubscribe(self, PoloniexEventBus.OrderBookSubscribers)
  }

  def receive = myReceive

  def myReceive: Receive = {
    case Subscribe(marketName) =>
      if (!markets.contains(marketName)) {
        // fire up a new actor for this market
        markets += marketName -> context.actorOf(PoloniexOrderBookSubscriber.props(conf, marketName), marketName)
        subscriberCounts += marketName -> 0
      }

      // inc the subscriber count
      val count = subscriberCounts(marketName) + 1
      subscriberCounts.update(marketName, count)

    // Note this must be called after a subscribe
    case Unsubscribe(marketName) if (markets.contains(marketName)) =>
      // dec the subscriber count
      val count = subscriberCounts(marketName) - 1
      subscriberCounts.update(marketName, count)

      // if no more subscribers shut the order book subscriber down
      if (subscriberCounts(marketName) == 0) {
        val actorRef = markets(marketName)
        actorRef ! DoDisconnect
        actorRef ! PoisonPill
        markets.remove(marketName)
      }
  }
}
