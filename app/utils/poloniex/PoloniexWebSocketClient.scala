package utils.poloniex

// external
import akka.actor._
import akka.wamp._
import akka.wamp.Wamp.Connected
import akka.wamp.messages._

// internal
import models.poloniex.{MarketStatus, Market}

class PoloniexWebSocketClient extends Actor with ActorLogging with Scope.Session {
  var transport: ActorRef = _
  var sessionId: Long = _
  val tickers = scala.collection.mutable.Map[String, MarketStatus]()

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
              recordUpdate(update)
            case None =>
              log info "received payload arguments not equal to 10"
          }
        case None =>
          log debug s"Event received for subscription $subscriptionId"
      }
    case Abort =>
      log debug s"poloniex websocket aborted"
      this.sessionId = 0
    case x =>
      log info x.toString
  }

  def processPayload(list: List[Any]): Option[Market] = {
    // number of arguments could change but as of 8/14/16 there are
    // 10 arguments in poloniex's websocket ticker feed
    // List(BTC_LSK, 0.00043800, 0.00043720, 0.00043659, 0.02693957, 481.98338671, 1104065.03193835, 0, 0.00045139, 0.00041697)
    // currencyPair, last, lowestAsk, highestBid, percentChange, baseVolume, quoteVolume, isFrozen, 24hrHigh, 24hrLow
    if (list.length == 10) {
      val args = list.map(_.toString)
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

  def recordUpdate(update: Market) = {
    import org.joda.time._

    def roundDateDownToMinute(dateTime: DateTime, minutes: Int): DateTime = {
      if (minutes < 1 || 5 % minutes != 0) {
        throw new IllegalArgumentException("minutes must be a factor of 5")
      }

      val m = dateTime.getMinuteOfHour() / minutes
      new DateTime(dateTime.getYear(),
        dateTime.getMonthOfYear(),
        dateTime.getDayOfMonth,
        dateTime.getHourOfDay(),
        m * minutes
      )
    }

    val ticker = update.ticker
    // only care about BTC markets
    if (ticker.startsWith("BTC")) {
      // get existing ticker from map
      tickers.get(ticker) match {
        case Some(previousUpdate) =>
          // was there a change in the price?
          val delta = update.status.last - previousUpdate.last
          //val volume = update.baseVolume + previousUpdate.baseVolume

          if (delta > 0) {
            val now = new DateTime()
            println(s"$now $ticker")
            val timeperiod = roundDateDownToMinute(now, 5)
            println(s"$timeperiod last ${update.status.last}")
            println()
            // two things you need to know very quickly about the update
            // what market ticker is it, the current time interval
            // if a new time interval we start a new candle
            // if in a current time interval we have to update the current candle
          }
          tickers.put(ticker, update.status)
        case None =>
          tickers.put(ticker, update.status)
      }
    }
  }
}

