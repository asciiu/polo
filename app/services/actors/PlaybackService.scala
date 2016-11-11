package services.actors

// internal
import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import models.market.MarketStructures.BollingerBandPoint
import play.api.libs.json.Json
import services.actors.PoloniexMarketService.GetBands
import slick.backend.DatabasePublisher

import scala.math.BigDecimal.RoundingMode
import scala.concurrent.ExecutionContext

// external
import models.db.Tables
import models.db.Tables._
import models.db.Tables.profile.api._
import models.market.MarketCandle
import models.market.MarketStructures.{ClosePrice, ExponentialMovingAverage, MarketMessage, PeriodVolume}
import models.analytics.individual.KitchenSink
import models.market.MarketStructures.Trade
import models.strategies.NeuralDataStrategy
import services.DBService
import utils.Misc


object PlaybackService{
  def props(out: ActorRef, database: DBService, sessionId: Int)(implicit context: ExecutionContext) =
    Props(new PlaybackService(out, database, sessionId))

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
  with KitchenSink {

  import PlaybackService._

  implicit lazy val materializer = ActorMaterializer()

  val marketName = "Variable"
  var myMarket = ""
  var count = 0

  // TODO this will eventually exercise some trade strategy based upon pattern
  // recognition
  //val strategy = new GoldenCrossStrategy(this)
  val strategy = new NeuralDataStrategy(this)



  override def preStart() = {
    log.info("Started a playback session")
  }

  override def postStop() = {
    log.info("Ended a playback session")
  }

  def receive: Receive = {
    // send updates from Bitcoin markets only
    case msg: MarketMessage =>
      //strategy.handleMessage(msg)

    case Done =>
      analyzeCandles()
      sendTCandles(myMarket)
      //strategy.printResults()

    case name: String =>
      // TODO there is better way to do this
      if (name == "play") {
        strategy.reset()
        //strategy.train()
        playbackMessages(myMarket)
      } else {
        myMarket = name
        sendCandles(name)
      }
  }

  def analyzeCandles(): Unit = {
    strategy.createDataFromCandles(myMarket)
  }

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

      val sortedCandles = sortedReverse.map(c =>
        new MarketCandle(c.createdAt, 5, c.open, c.close, c.highestBid, c.lowestAsk)).toList

      // clear previously stored market candles
      marketCandles.clear()
      appendCandles(sortedCandles)

      val closePrices = sortedReverse.map(c => ClosePrice(c.createdAt, c.close)).toList
      resetAverages()
      setAverages(closePrices)
      val movingAverages = getMovingAverages()

      reset()
      computeBands(closePrices)
      val bollingers = getAllPoints()

      val jsCandles = candles.map { c =>
        val time = c.createdAt.toEpochSecond() * 1000L - 2.16e+7
        val defaultEMA = ExponentialMovingAverage(c.createdAt, BigDecimal(0), 0)
        val ema1 = movingAverages.head._2.find( avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema
        val ema2 = movingAverages.last._2.find( avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema
        val bands = bollingers.find(b => c.createdAt.equals(b.time)).getOrElse(BollingerBandPoint(c.createdAt, 0, 0, 0))

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
          bands.center,
          bands.upper,
          bands.lower,
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
    //val candles = getMarketCandles()
    val candles = getCandles()
    val movingAverages = getMovingAverages()
    val vols = getVolumes()
    val bollingers = getAllPoints()

    val jsCandles = candles.map { c =>
      val time = c.time.toEpochSecond() * 1000L - 2.16e+7
      val defaultEMA = ExponentialMovingAverage(c.time, BigDecimal(0), 0)
      val defaultTrade = Trade(marketName, c.time, 0, 0)
      val ema1 = movingAverages.head._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema
      val ema2 = movingAverages.last._2.find( avg => c.time.equals(avg.time)).getOrElse(defaultEMA).ema
      val bands = bollingers.find(b => c.time.equals(b.time)).getOrElse(BollingerBandPoint(c.time, 0, 0, 0))
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
        bands.center,
        bands.upper,
        bands.lower,
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
