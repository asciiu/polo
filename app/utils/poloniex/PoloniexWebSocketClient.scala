package utils.poloniex

// external
import akka.actor._
import akka.wamp._
import akka.wamp.client._
import javax.inject.Inject
import play.api.Configuration
import org.joda.time.DateTime
import scala.concurrent.ExecutionContext

// internal
import models.poloniex.{MarketStatus, Market}

class PoloniexWebSocketClient @Inject() (conf: Configuration)(implicit context: ExecutionContext, system: ActorSystem) extends Actor with ActorLogging with Scope.Session {
  val endpoint = conf.getString("poloniex.websocket").getOrElse("wss://api.poloniex.com")
  val eventBus = PoloniexEventBus()

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
    } yield ()
  }

  def receive: Receive = {
    case x => log.warning(x.toString)
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

