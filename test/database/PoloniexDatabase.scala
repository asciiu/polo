package database

// external
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import slick.backend.DatabasePublisher

// internal
import models.db.Tables.profile.api._
import models.db.Tables.{PoloniexCandleRow, _}
import models.market.MarketEMACollection
import models.db.Tables
import models.market.MarketStructures.ClosePrice
import models.market.MarketStructures.MarketMessage

/**
  * Methods that make it convenient to access poloniex database
  */
trait PoloniexDatabase extends Postgres with ScalaFutures {

  val sessionId = 26
  /**
    * This assembles exponential moving averages (EMA) for each candle period for
    * candle data in the DB.
    *
    * @param periods a list of periods to compute EMAs for: e.g. 7-period ema, 15-period ema
    * @return
    */
  def exponentialMovingAverages(periods: List[Int] = List(7, 15)): Map[String, List[MarketEMACollection]] = {
    // the length of a period is 5 minutes
    val periodMinutes = 5

    // only read BTC candles in DB
    val query = PoloniexCandle.filter(candle => candle.cryptoCurrency.startsWith("BTC_") && candle.sessionId === sessionId)
    val result: Future[Seq[PoloniexCandleRow]] = database.run(query.result)
    // market name -> moving averages computed for all DB candles
    val marketCandles = scala.collection.mutable.Map[String, List[MarketEMACollection]]()

    val averages: Future[Map[String, List[MarketEMACollection]]] = result.map { candleRows =>

      // market name -> candle rows
      val groupByMarket: Map[String, Seq[PoloniexCandleRow]] = candleRows.groupBy(_.cryptoCurrency)

      for (marketName <- groupByMarket.keys) {
        // sort by descending time
        val sortedRows = groupByMarket(marketName).sortBy(_.createdAt).reverse
        // list of close prices assembled from candle periods in DB
        val closePrices = sortedRows.map { candleRow => ClosePrice(candleRow.createdAt, candleRow.close) }.toList

        val averagesList = for (period <- periods)
          yield new MarketEMACollection(marketName, period, periodMinutes, closePrices)

        marketCandles.put(marketName, averagesList)
      }

      marketCandles.toMap
    }

    Await.result(averages, Duration.Inf)
    averages.futureValue
  }

  /**
    * Provides a message source to be used in an Akka stream that
    * can stream the messages in the DB.
    *
    * @return a message source
    */
  def messageSource: Source[Tables.PoloniexMessageRow, NotUsed] = {
    // only BTC messages sorted by created time
    val query = Tables.PoloniexMessage.filter(msg => msg.cryptoCurrency.startsWith("BTC_") && msg.sessionId === sessionId).sortBy(_.createdAt).result
    val publisher: DatabasePublisher[Tables.PoloniexMessageRow] = database.stream(
      query.transactionally.withStatementParameters(fetchSize = 1000)
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
}
