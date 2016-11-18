package services.actors.orderbook

// external
import akka.actor._
import akka.io._
import akka.wamp._
import akka.wamp.client._
import akka.wamp.messages._
import java.time.{OffsetDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import models.market.{MarketOrderBook, OrderBook, OrderLine}
import play.api.Configuration
import play.api.libs.json.{JsPath, JsSuccess, Reads}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.libs.functional.syntax._

import scala.concurrent.duration._
import scala.language.postfixOps

// internal
import models.poloniex.{MarketEvent, PoloniexEventBus}
import models.market.MarketStructures.{OrderBookModify, OrderBookRemove}
import models.market.MarketStructures.Trade



object PoloniexOrderBook {
  def props(ws: WSClient, conf: Configuration, marketName: String) =
    Props(new PoloniexOrderBook(ws, conf, marketName))

  case object DoConnect
  case object DoShutdown
}

class PoloniexOrderBook(ws: WSClient, conf: Configuration, marketName: String)  extends Actor
  with ActorLogging
  with ClientContext {

  import PoloniexOrderBook._

  val manager = IO(Wamp)
  val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  val eventBus = PoloniexEventBus()

  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
  var shutdown = false

  val book = new MarketOrderBook(marketName)

  override def preStart(): Unit = {
    retrieveOrderBook()
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
      log.info(s"Started order book $marketName")
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

          if (order.side == "bid") book.removeBids(rate)
          else book.removeAsks(rate)

          eventBus.publish(MarketEvent(s"${PoloniexEventBus.Orders}/$marketName", order))

        // [{data: {rate: '0.00300888', type: 'bid', amount: '3.32349029'},type: 'orderBookModify'}]
        case (Some(t), Some(d)) if (t == "orderBookModify") =>
          val map = d.asInstanceOf[Map[String, String]]
          val side = map("type")
          val rate = BigDecimal(map("rate"))
          val amount = BigDecimal(map("amount"))
          val order = OrderBookModify(marketName, side, rate, amount)

          if (order.side == "bid") book.updateBids(rate, amount)
          else book.updateBids(rate, amount)

          eventBus.publish(MarketEvent(s"${PoloniexEventBus.Orders}/$marketName", order))

        case (Some(t), Some(d)) if (t == "newTrade") =>
          val map = d.asInstanceOf[Map[String, String]]
          val tradeID = map("tradeID").toInt
          val rate = BigDecimal(map("rate"))
          val amount = BigDecimal(map("amount"))
          val total = BigDecimal(map("total"))
          val side = map("type")
          val date = ZonedDateTime.parse(map("date"), formatter)
          val trade = Trade(marketName, date.toOffsetDateTime, tradeID, side, rate, amount, total)
          eventBus.publish(MarketEvent(s"${PoloniexEventBus.Orders}/$marketName", trade))

        case _ =>
      }
    }
  }

  private def retrieveOrderBook() = {

    val orderRead = Reads[List[OrderLine]](js =>
      js.validate[List[List[BigDecimal]]].map[List[OrderLine]] { entries =>
        entries.map ( e => OrderLine(e.head, e.last))
      })

    implicit val orderBookReads: Reads[OrderBook] = (
      (JsPath \ "asks").read[List[OrderLine]](orderRead) and
        (JsPath \ "bids").read[List[OrderLine]](orderRead)
      )(OrderBook.apply _)

    val poloniexRequest: WSRequest = ws.url("https://poloniex.com/public?command=returnOrderBook")
      .withHeaders("Accept" -> "application/json")
      .withQueryString("currencyPair" -> marketName)
      .withQueryString("depth" -> "50")
      .withRequestTimeout(10000.millis)

    poloniexRequest.get().map { polo =>
      polo.json.validate[OrderBook] match {
        case JsSuccess(orderBook, t) =>
          book.asks(orderBook.asks)
          book.bids(orderBook.bids)
        case _ =>
      }
    }
  }
}

