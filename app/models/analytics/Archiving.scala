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
import models.market.MarketStructures.MarketMessage
import models.db.Tables
import models.db.Tables.profile.api._
import models.db.Tables._
import models.db.OrderType
import services.DBService
import utils.Misc

/**
  * Provides DB archiving of messages and candles.
  */
trait Archiving extends ActorLogging {

  this: ReceivePipeline => pipelineInner {
    case msg: MarketMessage =>

      sessionId.map { id =>
        val newRow = convertUpdate(msg, id)
        val insertStatement = Tables.PoloniexMessage += newRow
        database.runAsync(insertStatement)
      }

      Inner(msg)

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

  private def convertUpdate(msg: MarketMessage, id: Int): PoloniexMessageRow = {
    PoloniexMessageRow(
      id = -1,
      sessionId = id,
      cryptoCurrency = msg.cryptoCurrency,
      last = msg.last,
      lowestAsk = msg.lowestAsk,
      highestBid = msg.highestBid,
      percentChange = msg.percentChange,
      baseVolume = msg.baseVolume,
      quoteVolume = msg.quoteVolume,
      isFrozen = if(msg.isFrozen == "0") false else true,
      high24hr = msg.high24hr,
      low24hr = msg.low24hr,
      createdAt = msg.time,
      updatedAt = msg.time
    )
  }

  protected def beginSession(marketCandles: List[Candles]) = {
    val time = Misc.now()
    val insert = (PoloniexSessions returning PoloniexSessions.map(_.id)) += PoloniexSessionsRow(-1, Some("New session"), time, None)

    database.runAsync(insert).map { id =>
      log.info(s"Capture session initiated (id: $id)")
      sessionId = Some(id)
      val newRows = marketCandles.flatMap( candles => convertCandles(candles, id) )
      val insertStatement = Tables.PoloniexCandle ++= newRows

      // set the session ID after this insert since it could be
      // potentially very large we want to wait for the DB to finish
      database.runAsync(insertStatement).map { count =>
        log.info(s"Captured $count candles in DB")
      }
    }
  }

  protected def endSession() = {
    sessionId.map { id =>
      val query = for {session <- PoloniexSessions if session.id === id} yield session.endedAt
      val updateAction = query.update(Some(Misc.now()))

      database.runAsync(updateAction).map{ count =>
        sessionId = None
        log.info(s"Capture session ended (id: $id)")
      }
    }
  }

  private def convertCandles(candles: Candles, sessionId: Int): List[PoloniexCandleRow] = {
    val name = candles.marketName
    candles.candles.map( c => PoloniexCandleRow(-1, sessionId, name, c.open, c.close, c.low, c.high, OffsetDateTime.parse(c.time.toString)))
  }

  def getSessionId = sessionId

  protected def insertOrder(marketName: String, price: BigDecimal, quantity: BigDecimal, orderType: OrderType.Value, time: OffsetDateTime) = {
    val newRow = PoloniexOrdersRow(-1, marketName, price, quantity, price*quantity, orderType, time, time)
    val insert = (PoloniexOrders += newRow)
    database.runAsync(insert)
  }
}

