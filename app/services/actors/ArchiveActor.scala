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
import Tables.{PoloniexCandleRow, PoloniexMessageRow}
import play.api.Configuration
import services.actors.CandleManagerActor.SetCandles

import scala.language.implicitConversions

class ArchiveActor @Inject() (database: DBService,
                              conf: Configuration) (implicit context: ExecutionContext) extends Actor with ActorLogging {

  val eventBus = PoloniexEventBus()
  val isCaptureMode = conf.getBoolean("poloniex.capture").getOrElse(false)

  implicit val timeout = Timeout(5 seconds)

  override def preStart() = {

    if (isCaptureMode) {
      // receive messages from exponential moving average
      eventBus.subscribe(self, "/market/update")
      eventBus.subscribe(self, "/market/candles")
    }
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/update")
    eventBus.unsubscribe(self, "/market/candles")
  }

  implicit def convertUpdate(update: MarketUpdate): PoloniexMessageRow = {
    val now = OffsetDateTime.now()
    PoloniexMessageRow(
      id = -1,
      cryptoCurrency = update.name,
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

  private def convertCandles(candles: SetCandles): List[PoloniexCandleRow] = {
    val name = candles.marketName
    candles.candles.map( c => PoloniexCandleRow(-1, name, c.open, c.close, c.low, c.high, OffsetDateTime.parse(c.time.toString)))
  }

  def receive = {
    case update: MarketUpdate =>
      database.runAsync((Tables.PoloniexMessage returning Tables.PoloniexMessage.map(_.id)) += update)

    case candles: SetCandles =>
      val insertStatement = Tables.PoloniexCandle ++= convertCandles(candles)
      database.runAsync(insertStatement)
  }
}
