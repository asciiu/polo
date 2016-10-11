package services.actors

import java.time.OffsetDateTime
import javax.inject.Inject

import akka.actor.{Actor, ActorLogging}
import TradeActor.MarketEMA
import models.market.ExponentialMovingAverages
import models.poloniex.{MarketEvent, PoloniexEventBus}
import org.joda.time.DateTime
import play.api.Configuration

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

// internals
import models.market.{ClosePrice, EMA}

object ExponentialMovingAverageActor {
  trait EMAMessage

  case class MarketCandleClose(marketName: String, close: ClosePrice)
  case class MarketCandleClosePrices(marketName: String, prices: List[ClosePrice]) extends EMAMessage
  case class AddEMAPeriod(periods: Int) extends EMAMessage
  case class GetMovingAverage(marketname: String, time: OffsetDateTime) extends EMAMessage
  case class GetMovingAverages(marketName: String) extends EMAMessage
}

/**
  * This Actor shall be responsible for tracking moving averages for markets.
  */
class ExponentialMovingAverageActor @Inject() (configuration: Configuration) extends Actor with ActorLogging {
  import ExponentialMovingAverageActor._

  val eventBus = PoloniexEventBus()

  // maps (MarketName, NumberOfPeriods) -> List[EMA]
  // number of periods is the number of candles to compute average for: example 7 candles
  // the list of ema shall be order in descending date with more recent moving average at head
  //val movingAverages = scala.collection.mutable.Map[(String, Int),  ListBuffer[EMA]]()

  // tracking periods pertains to ema periods that this actor is responsible for tracking
  val trackingPeriods = scala.collection.mutable.ListBuffer.empty[Int]

  val movingAverages = new ExponentialMovingAverages()

  override def preStart() = {
    log info "started"
    eventBus.subscribe(self, "/market/candle/close")
    eventBus.subscribe(self, "/market/prices")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/market/candle/close")
    eventBus.unsubscribe(self, "/market/prices")
  }

  def receive = {

    /**
      * Returns a List[(Int, BigDecimal)] to the sender
      */
    case GetMovingAverage(marketName, time) =>
      val avgs = movingAverages.getMovingAverages(marketName, time).map(a =>  (a._1, a._2)).toList
      sender ! avgs

    /**
      * Send to original sender the moving averages of a market.
      * retunrs a List[(Int, List[EMA])]
      */
    case GetMovingAverages(marketName) =>
      val allAvgs = movingAverages.getMovingAverages(marketName).toList
      sender ! allAvgs.toList

    case MarketCandleClose(marketName, close) =>
      val updates = movingAverages.update(marketName, close).toList
      if (updates(0)._2.ema != 0 && updates(1)._2.ema != 0) {
        eventBus.publish(MarketEvent("/ema/update", MarketEMA(marketName, updates(0)._2, updates(1)._2)))
      }

    case MarketCandleClosePrices(marketName, prices) =>
      movingAverages.setInitialMarketClosePrices(marketName, prices)
  }


}
