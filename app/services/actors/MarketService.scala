package services.actors

// external
import akka.actor.{Actor, ActorLogging, Props}
import akka.contrib.pattern.ReceivePipeline
import play.api.libs.json.{JsArray, Json}
import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

// internal
import models.analytics.Archiving
import models.analytics.individual.KitchenSink
import models.market.MarketStructures._
import models.poloniex.{MarketEvent, PoloniexEventBus}
import models.strategies.BollingerAlertStrategy
import services.DBService

object MarketService {
  def props(marketName: String, database: DBService)(implicit context: ExecutionContext) =
    Props(new MarketService(marketName, database))

  case object ReturnAllData
  case object ReturnLatestMessage
  case class Update(message: MarketMessage, candleData: JsArray)
}

class MarketService(val marketName: String, val database: DBService) extends Actor
  with ActorLogging
  with ReceivePipeline
  with KitchenSink
  with Archiving {

  import MarketService._

  val eventBus = PoloniexEventBus()
  val strategy = new BollingerAlertStrategy(this)
  var myLastUSDPrice: BigDecimal = 0.0

  private def publishUpdate(msg: MarketMessage) = {
    val averages = getLatestMovingAverages()

    getLatestCandle() match {
      case Some(candle) if (averages.nonEmpty) =>
        val volume24Hr = getVolume(candle.time)
        val bollingers = getLatestPoints()
        val bands = bollingers.getOrElse(BollingerBandPoint(candle.time, 0, 0, 0))
        val candleData = Json.arr(
          // TODO UTF offerset should come from client
          candle.time.toEpochSecond() * 1000L - 2.16e+7,
          candle.open,
          candle.high,
          candle.low,
          candle.close,
          averages.head._2,
          averages.last._2,
          volume24Hr.btcVolume.setScale(2, RoundingMode.DOWN),
          bands.center,
          bands.upper,
          bands.lower,
          myLastUSDPrice
        )
        val update = Update(msg, candleData)
        eventBus.publish(MarketEvent(PoloniexEventBus.Updates + s"/$marketName", update))

      case _ =>
    }
  }

  private def getAllData: List[JsArray] = {
    val candles = getCandles()
    val movingAverages = getMovingAverages()
    val volume24Hr = getVolumes()
    val bollingers = getAllPoints()

    candles.map { c =>
      val defaultEMA = ExponentialMovingAverage(c.time, 0, c.close)
      val bands = bollingers.find(b => c.time.equals(b.time)).getOrElse(BollingerBandPoint(c.time, 0, 0, 0))

      Json.arr(
        // TODO UTF offerset should come from client
        // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
        c.time.toEpochSecond() * 1000L - 2.16e+7,
        c.open,
        c.high,
        c.low,
        c.close,
        movingAverages.head._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema,
        movingAverages.last._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema,
        volume24Hr.find( vol => c.time.equals(vol.time)).getOrElse(PeriodVolume(c.time, 0)).btcVolume.setScale(2, RoundingMode.DOWN),
        bands.center,
        bands.upper,
        bands.lower
      )
    }
  }

  override def preStart() = {
    eventBus.subscribe(self, PoloniexEventBus.BTCPrice)
  }
  override def postStop() = {
    eventBus.unsubscribe(self, PoloniexEventBus.BTCPrice)
    log.info(s"Shutdown $marketName service")
  }

  def receive: Receive = {
    case msg: MarketMessage =>
      strategy.handleMessage(msg)
      publishUpdate(msg)

    case PriceUpdateBTC(time, price) =>
      val last = getLatestMessage()
      if (last.isDefined) {
        myLastUSDPrice = last.get.last * price
      }

    /**
      * Returns List[JsArray]
      */
    case ReturnAllData =>
      sender ! getAllData

    case ReturnLatestMessage =>
      sender ! getLatestMessage
  }
}

