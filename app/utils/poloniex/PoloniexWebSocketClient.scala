package utils.poloniex

// external
import akka.actor._
import akka.io.IO
import akka.wamp._
import akka.wamp.Wamp.Connected
import akka.wamp.messages._
import org.joda.time.DateTime

// internal
import models.poloniex.{MarketStatus, Market}


object PoloniexWebSocketClient {
  def props()(implicit system: ActorSystem): Props = Props(new PoloniexWebSocketClient())
}

class PoloniexWebSocketClient(implicit system: ActorSystem) extends Actor with ActorLogging with Scope.Session {
  var transport: ActorRef = _
  var sessionId: Long = _

  override def preStart(): Unit = {
    import Wamp._
    import messages._
    IO(Wamp) ! Connect(self, url = "wss://api.poloniex.com")
  }

  override def postStop(): Unit = {
    log info "closing poloniex websocket"
    transport ! Goodbye()
  }

  def receive: Receive = {
    case Connected(transport) =>
      log info "connected to poloniex websocket"
      this.transport = transport
      this.transport ! Hello("realm1")

    case Welcome(sessionId, _) =>
      log info "subscribing to poloniex ticker"
      transport ! Subscribe(nextId(), topic = "ticker")
      context become opened
  }

  def opened: Receive = {
    case Subscribed(_, _) =>
      log info "subscribed to poloniex 'ticker'"

    case Event(subscriptionId, publicationId, payload, details) =>
      details match {
        case Some(payload) =>
          processPayload(payload.arguments) match {
            case Some(update) =>
              context.parent ! update
            case None =>
              log info "received payload arguments not equal to 10"
          }
        case None =>
          log debug s"Event received for subscription $subscriptionId"
      }
    case Abort =>
      log debug s"poloniex websocket aborted"
      this.sessionId = 0
    case x  =>
      log debug x.toString
      throw new Exception
  }

  def processPayload(list: List[Any]): Option[Market] = {
    // number of arguments could change but as of 8/14/16 there are
    // 10 arguments in poloniex's websocket ticker feed
    // List(BTC_LSK, 0.00043800, 0.00043720, 0.00043659, 0.02693957, 481.98338671, 1104065.03193835, 0, 0.00045139, 0.00041697)
    // currencyPair, last, lowestAsk, highestBid, percentChange, baseVolume, quoteVolume, isFrozen, 24hrHigh, 24hrLow
    if (list.length == 10) {
      val args = list.map(_.toString)
      var now = new DateTime()
      val status = MarketStatus(
        0,
        BigDecimal(args(1)),
        BigDecimal(args(2)),
        BigDecimal(args(3)),
        BigDecimal(args(4)),
        BigDecimal(args(5)),
        BigDecimal(args(6)),
        args(7),
        BigDecimal(args(8)),
        BigDecimal(args(9))
      )
      Some(Market(args(0),status))
    } else {
      None
    }
  }
}

