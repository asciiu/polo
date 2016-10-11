package services.actors

// external
import javax.inject.Inject

import akka.actor._
import akka.wamp._
import akka.wamp.client._
import play.api.Configuration

import scala.concurrent.ExecutionContext

// internal
import models.poloniex.{MarketEvent, MarketMessage, MarketUpdate, PoloniexEventBus}

class PoloniexWebSocketClient @Inject() (conf: Configuration)(implicit context: ExecutionContext, system: ActorSystem) extends Actor with ActorLogging with Scope.Session {
  val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  val eventBus = PoloniexEventBus()
  private var openSession: Option[Session] = None

  override def preStart(): Unit = {
    for {
      session <- Client().connectAndOpen(url = endpoint, realm = "realm1")
      subscription <- session.subscribe("ticker") { event =>
        log.debug(s"received $event")
        event.payload.map{ p =>
          processPayload(p.arguments) match {
            case Some(update) =>
              eventBus.publish(MarketEvent("/market/update", update))
              //context.parent ! update
            case None =>
              log info "received payload arguments not equal to 10"
          }}
        }
    } yield {
      openSession = Some(session)
    }
  }

  override def postStop(): Unit = {
    openSession.foreach( session => session.close() )
  }

  def receive: Receive = {
    case x => log.warning(x.toString)
  }

  def processPayload(list: List[Any]): Option[MarketUpdate] = {
    // number of arguments could change but as of 8/14/16 there are
    // 10 arguments in poloniex's websocket ticker feed
    // List(BTC_LSK, 0.00043800, 0.00043720, 0.00043659, 0.02693957, 481.98338671, 1104065.03193835, 0, 0.00045139, 0.00041697)
    // currencyPair, last, lowestAsk, highestBid, percentChange, baseVolume, quoteVolume, isFrozen, 24hrHigh, 24hrLow
    if (list.length == 10) {
      val args = list.map(_.toString)
      val status = MarketMessage(
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
      Some(MarketUpdate(args(0),status))
    } else {
      None
    }
  }
}

