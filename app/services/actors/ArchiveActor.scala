package services.actors

// external
import java.time.OffsetDateTime
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import models.db.Tables
import models.poloniex.{MarketUpdate, PoloniexEventBus}
import services.DBService

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import models.db.Tables.profile.api._
import models.db.Tables.{PoloniexCandleRow, PoloniexMessageRow, PoloniexSessions, PoloniexSessionsRow}
import play.api.Configuration
import services.actors.CandleManagerActor.SetCandles
import utils.Misc

import scala.language.implicitConversions

object ArchiveActor {

  trait ArchiveActorMsg
  case object StartCapture extends ArchiveActorMsg
  case object EndCapture extends ArchiveActorMsg
}

class ArchiveActor @Inject() (database: DBService,
                              conf: Configuration) (implicit context: ExecutionContext) extends Actor with ActorLogging {

  import ArchiveActor._
  val eventBus = PoloniexEventBus()
  var sessionId: Option[Int] = None

  implicit val timeout = Timeout(5 seconds)

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/update")
    eventBus.unsubscribe(self, "/market/candles")
  }

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

  private def convertCandles(candles: SetCandles, sessionId: Int): List[PoloniexCandleRow] = {
    val name = candles.marketName
    candles.candles.map( c => PoloniexCandleRow(-1, sessionId, name, c.open, c.close, c.low, c.high, OffsetDateTime.parse(c.time.toString)))
  }

  def receive = {
    case update: MarketUpdate =>
      sessionId.map { id =>
        val newRow = convertUpdate(update, id)
        database.runAsync((Tables.PoloniexMessage returning Tables.PoloniexMessage.map(_.id)) += newRow)
      }

    case candles: SetCandles =>
      sessionId.map { id =>
        val newRow = convertCandles(candles, id)
        val insertStatement = Tables.PoloniexCandle ++= newRow
        database.runAsync(insertStatement)
      }

    case StartCapture =>
      val time = Misc.now()
      val insert = (PoloniexSessions returning PoloniexSessions.map(_.id)) += PoloniexSessionsRow(-1, Some("New session"), time, None)

      database.runAsync(insert).map { id =>
        sessionId = Some(id)
        eventBus.subscribe(self, "/market/update")
        eventBus.subscribe(self, "/market/candles")

        // TODO fix this you need to obtain all candles from the CandleManager now
        // because I do not subscribe to the initial setting of candles upon startup anymore
        // I could start a record session at any time!!
      }

    case EndCapture =>
      sessionId.map { id =>
        // unsubscribe from all updates
        eventBus.unsubscribe(self, "/market/update")
        eventBus.unsubscribe(self, "/market/candles")

        val query = for {session <- PoloniexSessions if session.id === id} yield session.endedAt
        val updateAction = query.update(Some(Misc.now()))

        database.runAsync(updateAction).map(id =>  sessionId = None)
      }
  }
}

