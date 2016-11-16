package services.actors

import akka.actor.{Actor, ActorRef, Props}
import play.api.libs.json.Json
import services.DBService

import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

// internal
import models.market.MarketStructures.{MarketMessage => Msg}
import models.poloniex.PoloniexEventBus


object BrowserService {
  def props(marketName: String, out: ActorRef, database: DBService)(implicit context: ExecutionContext) =
    Props(new BrowserService(marketName, out, database))

  case object Done
}

class BrowserService(marketName: String, out: ActorRef, database: DBService)(implicit ctx: ExecutionContext)
  extends Actor {

  val eventBus = PoloniexEventBus()

  implicit val msgWrite = Json.writes[Msg]

  override def preStart() = {
    eventBus.subscribe(self, PoloniexEventBus.Updates + s"/$marketName")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, PoloniexEventBus.Updates + s"/$marketName")
  }

  def receive = {
    // send updates from Bitcoin markets only
    case msg: Msg if msg.cryptoCurrency.startsWith("BTC") =>
      val percentChange = msg.percentChange * 100
      val ud = msg.copy(percentChange = percentChange.setScale(2, RoundingMode.CEILING))
      out ! Json.toJson(ud).toString
    case msg: Msg if msg.cryptoCurrency == "USDT_BTC" =>
      out ! Json.toJson(msg).toString
  }
}
