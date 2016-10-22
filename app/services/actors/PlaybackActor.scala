package services.actors

// internal
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import play.api.libs.json.{Json, JsString}
import scala.concurrent.ExecutionContext

// external
import models.db.Tables._
import models.db.Tables.profile.api._
import models.analytics.{ExponentialMovingAverages, LastMarketMessage, MarketCandles, Volume24HourTracking}
import models.market.MarketCandle
import models.market.MarketStructures.{ClosePrice, ExponentialMovingAverage, MarketMessage, PeriodVolume}
import services.DBService


object PlaybackActor{
  def props(out: ActorRef, database: DBService)(implicit context: ExecutionContext): Props = Props(new PlaybackActor(out, database))
}

/**
  * This actor will be responsible for serving data
  * captured in the DB. It will most likely be used
  * in the HistoryController to serve a browser web
  * socket connection.
  */
class PlaybackActor(out: ActorRef, database: DBService)(implicit executionContext: ExecutionContext) extends Actor
  with ActorLogging
  with ReceivePipeline
  with MarketCandles
  with ExponentialMovingAverages
  with Volume24HourTracking
  with LastMarketMessage {

  // TODO this needs to be parameterized some how
  val sessionID = 23
  override val periodMinutes = 5

  override def preStart() = {}

  override def postStop() = {}

  def receive = {
    // send updates from Bitcoin markets only
    case msg: MarketMessage => ???

    case marketName: String =>
      sendCandles(marketName)
  }

  // assum marketName will always exist for now, but this needs error handling
  def sendCandles(marketName: String): Unit = {
    log.info(s"Setting $marketName candles for new client")

    val queryCandles = PoloniexCandle.filter( c => c.sessionId === sessionID && c.cryptoCurrency === marketName).sortBy(_.createdAt)

    database.runAsync(queryCandles.result).map { candles =>
      val sortedReverse = candles.reverse

      val marketCandles = sortedReverse.map(c =>
        new MarketCandle(c.createdAt, 5, c.open, c.close, c.highestBid, c.lowestAsk)).toList

      appendCandles(marketName, marketCandles)

      val closePrices = sortedReverse.map(c => ClosePrice(c.createdAt, c.close)).toList
      setAverages(marketName, closePrices)
      val movingAverages = getMovingAverages(marketName)

      val jsCandles = candles.map { c =>
        val time = c.createdAt.toEpochSecond() * 1000L - 2.16e+7
        val defaultEMA = ExponentialMovingAverage(c.createdAt, BigDecimal(0), 0)
        val ema1 = movingAverages(0)._2.find( avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema
        val ema2 = movingAverages(1)._2.find( avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema

        Json.arr(
          // TODO UTF offerset should come from client
          // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
          time,
          c.open,
          c.highestBid,
          c.lowestAsk,
          c.close,
          ema1,
          ema2,
          0
        )
      }

      val json = Json.obj(
        "type" -> "MarketCandles",
        "data" -> jsCandles
      ).toString

      out ! json
    }
  }
}
