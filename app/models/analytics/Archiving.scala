package models.analytics

// external
import akka.actor.ActorLogging
import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.Inner
import akka.util.Timeout
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

// internal
import models.market.MarketStructures.Candles
import models.db.Tables
import models.db.Tables.profile.api._
import models.db.Tables.{PoloniexCandleRow, PoloniexMessageRow, PoloniexSessions, PoloniexSessionsRow}
import models.poloniex.MarketUpdate
import services.DBService
import utils.Misc

/**
  * Provides DB archiving of messages and candles.
  */
trait Archiving extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case update: MarketUpdate =>

      sessionId.map {id =>
        val newRow = convertUpdate (update, id)
        database.runAsync ((Tables.PoloniexMessage returning Tables.PoloniexMessage.map (_.id) ) += newRow)
      }

      Inner(update)

    case candles: Candles =>

      sessionId.map { id =>
        val newRow = convertCandles(candles, id)
        val insertStatement = Tables.PoloniexCandle ++= newRow
        database.runAsync(insertStatement)
      }

      Inner(candles)
  }

  def database: DBService
  var sessionId: Option[Int] = None

  implicit val timeout = Timeout(5 seconds)

  private def convertUpdate(update: MarketUpdate, sessionId: Int): PoloniexMessageRow = {
    val now = utils.Misc.now()
    PoloniexMessageRow(
      id = -1,
      sessionId,
      cryptoCurrency = update.marketName,
      last = update.info.last,
      lowestAsk = update.info.lowestAsk,
      highestBid = update.info.highestBid,
      percentChange = update.info.percentChange,
      baseVolume = update.info.baseVolume,
      quoteVolume = update.info.quoteVolume,
      isFrozen = if(update.info.isFrozen == "0") false else true,
      high24hr = update.info.high24hr,
      low24hr = update.info.low24hr,
      createdAt = now,
      updatedAt = now
    )
  }

  protected def beginSession() = {
    val time = Misc.now()
    val insert = (PoloniexSessions returning PoloniexSessions.map(_.id)) += PoloniexSessionsRow(-1, Some("New session"), time, None)

    database.runAsync(insert).map { id => sessionId = Some(id)}
  }

  protected def endSession() = {
    sessionId.map { id =>
      val query = for {session <- PoloniexSessions if session.id === id} yield session.endedAt
      val updateAction = query.update(Some(Misc.now()))

      database.runAsync(updateAction).map(id =>  sessionId = None)
    }
  }

  private def convertCandles(candles: Candles, sessionId: Int): List[PoloniexCandleRow] = {
    val name = candles.marketName
    candles.candles.map( c => PoloniexCandleRow(-1, sessionId, name, c.open, c.close, c.low, c.high, OffsetDateTime.parse(c.time.toString)))
  }
}
