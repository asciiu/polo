package services.actors

// external
import javax.inject.Inject

import akka.actor._
import akka.wamp._
import akka.wamp.client._
import play.api.Configuration

import scala.concurrent.duration._
import akka.io._
import akka.wamp.messages._
import scala.language.postfixOps

// internal
import models.market.MarketStructures.MarketMessage
import models.poloniex.{MarketEvent, PoloniexEventBus}
import utils.Misc


class PoloniexWebSocketFeedService @Inject()(conf: Configuration) extends Actor with ActorLogging {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  val eventBus = PoloniexEventBus()
  val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  val socketClient = context.actorOf(PoloniexWebSocketClient.props(endpoint))

  override def postStop() = {
    log info "shutting down"
    socketClient ! PoisonPill
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => {
        log debug s"$e encountered restart"
        Restart
      }
    }

  def receive: Receive = {
    case msg: MarketMessage =>
      eventBus.publish(MarketEvent(PoloniexEventBus.Updates, msg))
  }
}

object PoloniexWebSocketClient {
  def props(url: String) = Props(new PoloniexWebSocketClient(url))
}

class PoloniexWebSocketClient (endpoint: String) extends Actor
  with ActorLogging
  with ClientContext {

  import Client._

  val manager = IO(Wamp)
  //val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  //val eventBus = PoloniexEventBus()

  override def preStart(): Unit = {
    self ! DoConnect
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info(s"Closing the socket")
    manager ! Goodbye
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
      transport ! Subscribe(requestId, Dict(), "ticker")
  }


  var subscriptionId: Id = _
  def handleSession(transport: ActorRef, sessionId: Id): Receive = {
    case Subscribed(reqId, subId)  =>
      if (reqId == requestId)
        subscriptionId = subId

    case Event(subId, _, _, payload) =>
      if (subId == subscriptionId)

        payload.parsed.map { p =>
          processPayload(p.args) match {
            case Some(update) =>
              //eventBus.publish(MarketEvent(PoloniexEventBus.Updates, update))
              context.parent ! update
            case None =>
              log info "received payload arguments not equal to 10"
          }
        }

    case signal @ Disconnected =>
      context become receive
      self ! DoConnect
      log.info(s"Disconnected!! Attempting to reconnect")
  }

  private def processPayload(list: List[Any]): Option[MarketMessage] = {
    // number of arguments could change but as of 8/14/16 there are
    // 10 arguments in poloniex's websocket ticker feed
    // List(BTC_LSK, 0.00043800, 0.00043720, 0.00043659, 0.02693957, 481.98338671, 1104065.03193835, 0, 0.00045139, 0.00041697)
    // currencyPair, last, lowestAsk, highestBid, percentChange, baseVolume, quoteVolume, isFrozen, 24hrHigh, 24hrLow
    if (list.length == 10) {
      val args = list.map(_.toString)
      val time = Misc.now()
      val msg = MarketMessage(
        time = time,
        cryptoCurrency = args(0),
        last = BigDecimal(args(1)),
        lowestAsk = BigDecimal(args(2)),
        highestBid = BigDecimal(args(3)),
        percentChange = BigDecimal(args(4)),
        baseVolume = BigDecimal(args(5)),
        quoteVolume = BigDecimal(args(6)),
        isFrozen = args(7),
        high24hr = BigDecimal(args(8)),
        low24hr = BigDecimal(args(9))
      )
      Some(msg)
    } else {
      None
    }
  }

  object Client {
    case object DoConnect
  }
}

