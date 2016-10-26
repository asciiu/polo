package services.actors

// internal
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import models.strategies.GoldenCrossStrategy
import play.api.libs.json.Json
import slick.backend.DatabasePublisher
import utils.Misc

import scala.math.BigDecimal.RoundingMode
import scala.concurrent.ExecutionContext

// external
import models.db.Tables
import models.db.Tables._
import models.db.Tables.profile.api._
import models.analytics.{ExponentialMovingAverages, LastMarketMessage, MarketCandles, Volume24HourTracking}
import models.market.MarketCandle
import models.market.MarketStructures.{ClosePrice, ExponentialMovingAverage, MarketMessage, PeriodVolume}
import services.DBService


object PlaybackService{
  def props(out: ActorRef, database: DBService, sessionId: Int)(implicit context: ExecutionContext): Props = Props(new PlaybackService(out, database, sessionId))
  case object Done
}

/**
  * This actor will be responsible for serving data
  * captured in the DB. It will most likely be used
  * in the HistoryController to serve a browser web
  * socket connection.
  */
class PlaybackService(out: ActorRef, val database: DBService, sessionId: Int)(implicit executionContext: ExecutionContext) extends Actor
  with ActorLogging
  with ReceivePipeline
  with MarketCandles
  with Volume24HourTracking
  with LastMarketMessage
  with GoldenCrossStrategy {

  import PlaybackService._

  implicit lazy val materializer = ActorMaterializer()

  // TODO this needs to be parameterized some how
  override val periodMinutes = 5
  var market = ""
  var count = 0

  override def preStart() = {}

  override def postStop() = {}

  def myReceive: Receive = {
    // send updates from Bitcoin markets only
    case Done =>
      sendTCandles(market)
      printResults()

    case marketName: String =>
      if (marketName == "play") {
        reset()
        playbackMessages(market)
      }
      else {
        sendCandles(marketName)
        market = marketName
      }
  }

  def receive = myReceive orElse handleMessageUpdate

  def playbackMessages(marketName: String): Unit = {
    val sink = Sink.actorRef[MarketMessage](self, onCompleteMessage = Done)

    messageSource(marketName)
      .via(messageFlow)
      .to(sink)
      .run()
  }

  /**
    * Provides a message source to be used in an Akka stream that
    * can stream the messages in the DB.
    *
    * @return a message source
    */
  def messageSource(marketName: String): Source[Tables.PoloniexMessageRow, NotUsed] = {
    // only BTC messages sorted by created time
    val query = PoloniexMessage.filter(msg =>
      msg.cryptoCurrency === marketName && msg.sessionId === sessionId).sortBy(_.createdAt).result
    val publisher: DatabasePublisher[Tables.PoloniexMessageRow] = database.stream(
      query.transactionally.withStatementParameters(fetchSize = 500)
    )

    Source.fromPublisher(publisher)
  }

  /**
    * Converts a DB PoloniexMessageRow to a MarketMessage
    *
    * @return a flow that defines input PoloniexMessageRow to output MarketMessage
    */
  def messageFlow: Flow[Tables.PoloniexMessageRow, MarketMessage, NotUsed] = {
    // flow input is a message row
    Flow[Tables.PoloniexMessageRow].map { row =>
      MarketMessage(
        row.createdAt,
        row.cryptoCurrency,
        row.last,
        row.lowestAsk,
        row.highestBid,
        row.percentChange,
        row.baseVolume,
        row.quoteVolume,
        row.isFrozen.toString,
        row.high24hr,
        row.low24hr)
    }
  }

  // assum marketName will always exist for now, but this needs error handling
  def sendCandles(marketName: String): Unit = {
    val queryCandles = PoloniexCandle.filter( c => c.sessionId === sessionId && c.cryptoCurrency === marketName).sortBy(_.createdAt)

    database.runAsync(queryCandles.result).map { candles =>
      val sortedReverse = candles.reverse

      val marketCandles = sortedReverse.map(c =>
        new MarketCandle(c.createdAt, 5, c.open, c.close, c.highestBid, c.lowestAsk)).toList

      // clear previously stored market candles
      this.marketCandles.remove(marketName)
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
          0,
          0,
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

  def sendTCandles(marketName: String) = {
    val candles = getMarketCandles(marketName)
    val movingAverages = getMovingAverages(marketName)
    val vols = getVolumes(marketName)

    val jsCandles = candles.map { c =>
      val time = c.time.toEpochSecond() * 1000L - 2.16e+7
      val defaultEMA = ExponentialMovingAverage(c.time, BigDecimal(0), 0)
      val defaultTrade = Trade(marketName, c.time, 0, 0)
      val ema1 = movingAverages(0)._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema
      val ema2 = movingAverages(1)._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema
      val vol = vols.find( vol => c.time.equals(vol.time))
        .getOrElse(PeriodVolume(c.time, 0)).btcVolume.setScale(2, RoundingMode.DOWN)
      val buy = buyList.find( trade => Misc.roundDateToMinute(trade.time, periodMinutes).isEqual(c.time))
        .getOrElse(defaultTrade).price
      val sell = sellList.find( trade => Misc.roundDateToMinute(trade.time, periodMinutes).isEqual(c.time))
        .getOrElse(defaultTrade).price

      Json.arr(
        // TODO UTF offerset should come from client
        // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
        time,
        c.open,
        c.high,
        c.low,
        c.close,
        ema1,
        ema2,
        vol,
        buy,
        sell
      )
    }

    log.info(s"Sending $marketName candles after play")

    val json = Json.obj(
      "type" -> "MarketCandles",
      "data" -> jsCandles
    ).toString

    out ! json

//    getLatestCandle(marketName) match {
//      case Some(candle) =>
//        val averages = getMovingAverage(marketName, candle.time)
//        val vol = getVolume(marketName, candle.time)
//
//        val jsCandle = Json.arr(
//          // TODO UTF offerset should come from client
//          candle.time.toEpochSecond() * 1000L - 2.16e+7,
//          candle.open,
//          candle.high,
//          candle.low,
//          candle.close,
//          averages(0)._2,
//          averages(1)._2,
//          vol.btcVolume.setScale(2, RoundingMode.DOWN)
//        )
//
//        val json = Json.obj(
//          "type" -> "MarketCandle",
//          "data" -> jsCandle
//        ).toString
//
//        out ! json
//
//      case None =>
//    }
  }
}
