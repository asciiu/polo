package services.actors

import akka.actor.{Actor, ActorRef, Props}
import models.poloniex.MarketEvent
import play.api.libs.json.Json
import services.DBService
import services.actors.MarketService.Update
import services.actors.orderbook.PoloniexOrderBookManager.{Subscribe, Unsubscribe}

import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

// internal
import models.market.MarketStructures.{MarketMessage => Msg}
import models.poloniex.PoloniexEventBus


object MarketSocketService {
  def props(marketName: String, out: ActorRef, database: DBService)(implicit context: ExecutionContext) =
    Props(new MarketSocketService(marketName, out, database))

  case object Done
}

/**
  * This actor will service websocket connections that require updates
  * from a single market source.
  *
  * @param marketName
  * @param out an actor reference to the browser client
  * @param database
  * @param ctx
  */
class MarketSocketService(marketName: String, out: ActorRef, database: DBService)(implicit ctx: ExecutionContext)
  extends Actor {

  val eventBus = PoloniexEventBus()

  implicit val msgWrite = Json.writes[Msg]

  override def preStart() = {
    eventBus.subscribe(self, PoloniexEventBus.Updates + s"/$marketName")
    eventBus.publish(MarketEvent(PoloniexEventBus.OrderBookSubscribers, Subscribe(marketName)))
  }

  override def postStop() = {
    eventBus.unsubscribe(self, PoloniexEventBus.Updates + s"/$marketName")
    eventBus.publish(MarketEvent(PoloniexEventBus.OrderBookSubscribers, Unsubscribe(marketName)))
  }

  def receive = {

    case Update(msg, candleData) =>
      val percentChange = msg.percentChange * 100
      val marketMessage = msg.copy(percentChange = percentChange.setScale(2, RoundingMode.CEILING))
      val json = Json.obj(
        "type" -> "MarketMessage",
        "data" -> marketMessage,
        "candle" -> candleData
      ).toString

      out ! json
  }
}
