package services.actors

// external
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import models.market.EMA
import models.poloniex.{MarketMessage, MarketUpdate}
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.math.BigDecimal.RoundingMode

// internal
import utils.poloniex.PoloniexEventBus

object TradeActor {
  trait TradeMessage

  case class MarketEMA(marketName: String, emaShort: EMA, emaLong: EMA) extends TradeMessage
  case class GetLatestMessage(marketName: String) extends TradeMessage
}

/**
  * Created by bishop on 9/7/16.
  */
class TradeActor @Inject() () (implicit context: ExecutionContext) extends Actor with ActorLogging {
  import TradeActor._

  case class BuyRecord(date: DateTime, buyPrice: BigDecimal, amount: BigDecimal, last24BtcVolume: BigDecimal)

  // TODO track market volume as it happens how do you do this based upon the rolling 24 hour volume

  val marketSummaries = scala.collection.mutable.Map[String, MarketMessage]()

  // define a map for the market name to the change in volume
  private case class Volumes(first: BigDecimal, last: BigDecimal)
  private val marketVols = scala.collection.mutable.Map[String, Volumes]()

  val marketWatch = scala.collection.mutable.Set[String]()
  val eventBus = PoloniexEventBus()
  var lastBuy: BigDecimal = 0

  implicit val timeout = Timeout(5 seconds)

  override def preStart() = {
    // receive messages from exponential moving average
    eventBus.subscribe(self, "/ema/update")
    eventBus.subscribe(self, "/market/update")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/ema/update")
    eventBus.unsubscribe(self, "/market/update")
  }

  def receive = {
    case update: MarketUpdate =>
      marketSummaries(update.name) = update.info

      if (marketVols.contains(update.name)) {
        val volumes = marketVols(update.name)
        marketVols.update(update.name, Volumes(volumes.last, update.info.baseVolume))
      } else {
        marketVols.put(update.name, Volumes(0.0, update.info.baseVolume))
      }


    case GetLatestMessage(marketName) =>
      sender ! marketSummaries.get(marketName)

    // this needs to receive both computed emas
    // TODO change the EMA actor update alorithm to fix this
    case MarketEMA(marketName, emaShort, emaLong) => {
      // are we already watching this market
      if (marketWatch.contains(marketName) && emaShort.ema < emaLong.ema) {
        marketWatch.remove(marketName)
      } else if (emaShort.ema > emaLong.ema) {
        // TODO compute the slope from the last two period moving averages

        val vols = marketVols(marketName)
        if (vols.first != 0 && vols.last - vols.first > 3) {
          if (!marketWatch.contains(marketName)) {
            marketWatch.add(marketName)
          } else {
            val delta = (vols.last - vols.first).setScale(2, RoundingMode.DOWN)
            println(s"$marketName change in vol: $delta")
          }
        }
      }
    }
  }
}
