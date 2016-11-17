package services.actors.orderbook

// external
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import akka.actor._
import akka.io._
import akka.wamp._
import akka.wamp.client._
import akka.wamp.messages._
import models.market.MarketStructures.Trade
import play.api.Configuration

import scala.concurrent.duration._
import scala.language.postfixOps

// internal
import models.poloniex.{MarketEvent, PoloniexEventBus}
import models.market.MarketStructures.{OrderBookModify, OrderBookRemove}



object PoloniexOrderBookSubscriber {
  def props(conf: Configuration, marketName: String) =
    Props(new PoloniexOrderBookSubscriber(conf, marketName))

  case object DoConnect
  case object DoShutdown
}

class PoloniexOrderBookSubscriber (conf: Configuration, marketName: String)  extends Actor
  with ActorLogging
  with ClientContext {

  import PoloniexOrderBookSubscriber._

  val manager = IO(Wamp)
  val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  val eventBus = PoloniexEventBus()

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
  var shutdown = false

  override def preStart(): Unit = {
    self ! DoConnect
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info(s"Shutting down order book $marketName")
  }

  override def receive(): Receive = {
    case DoConnect =>
      manager ! Connect(endpoint, "wamp.2.json")

    case signal @ CommandFailed(cmd: Connect, ex) =>
      log.warning(ex.getMessage)
      scheduler.scheduleOnce(1 second, self, DoConnect)

    case signal @ Connected(transport) =>
      log.info(s"Connected $transport")
      context become handleConnected(transport)
      transport ! Hello("realm1")
  }

  var requestId: Id = _
  def handleConnected(transport: ActorRef): Receive = {
    case Welcome(sessionId, details) =>
      context become handleSession(transport, sessionId)
      requestId = nextRequestId()
      transport ! Subscribe(requestId, Dict(), marketName)
  }


  var subscriptionId: Id = _
  def handleSession(transport: ActorRef, sessionId: Id): Receive = {
    case Subscribed(reqId, subId)  =>
      if (reqId == requestId)
        subscriptionId = subId

    case Event(subId, _, _, payload) =>
      if (subId == subscriptionId)

        payload.parsed.map { p =>
          publishOrders(p.args)
        }

    case signal @ Disconnected =>
      if (!shutdown) {
        context become receive
        self ! DoConnect
        log.info(s"Disconnected!! Attempting to reconnect")
      } else {
        self ! PoisonPill
      }

    case DoShutdown =>
      // end session
      transport ! Goodbye
      transport ! Disconnect
      shutdown = true
  }

  /**
    * Publishes orders via event bus.
    *
    * @param list
    */
  private def publishOrders(list: List[Any]) = {
    val orders = list.asInstanceOf[List[Map[String, Any]]]
    orders.foreach { order =>
      val tp = order.get("type")
      val data = order.get("data")

      (tp, data) match {
        // [{data: {rate: '0.00311164', type: 'ask' },type: 'orderBookRemove'}]
        case (Some(t), Some(d)) if (t == "orderBookRemove") =>
          val map = d.asInstanceOf[Map[String, String]]
          val side = map("type")
          val rate = BigDecimal(map("rate"))
          val order = OrderBookRemove(marketName, side, rate)
          println(order)
          eventBus.publish(MarketEvent(PoloniexEventBus.Orders, order))

        // [{data: {rate: '0.00300888', type: 'bid', amount: '3.32349029'},type: 'orderBookModify'}]
        case (Some(t), Some(d)) if (t == "orderBookModify") =>
          val map = d.asInstanceOf[Map[String, String]]
          val side = map("type")
          val rate = BigDecimal(map("rate"))
          val amount = BigDecimal(map("amount"))
          val order = OrderBookModify(marketName, side, rate, amount)
          eventBus.publish(MarketEvent(PoloniexEventBus.Orders, order))

        case (Some(t), Some(d)) if (t == "newTrade") =>
          val map = d.asInstanceOf[Map[String, String]]
          val tradeID = map("tradeID").toInt
          val rate = BigDecimal(map("rate"))
          val amount = BigDecimal(map("amount"))
          val total = BigDecimal(map("total"))
          val side = map("type")
          val date = ZonedDateTime.parse(map("date"), formatter)
          val trade = Trade(marketName, date.toOffsetDateTime, tradeID, side, rate, amount, total)
          eventBus.publish(MarketEvent(PoloniexEventBus.Orders, trade))

        case _ =>
      }
    }
  }
}

