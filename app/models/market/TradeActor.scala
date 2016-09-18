package models.market

// external
import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import models.poloniex.{MarketUpdate, MarketCandle}
import org.joda.time.DateTime
import services.CandleManagerActor.GetLastestCandle

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

// internal
import utils.poloniex.PoloniexEventBus

object TradeActor {
  trait TradeMessage

  case class MarketEMA(marketName: String, emaShort: EMA, emaLong: EMA) extends TradeMessage
}

/**
  * Created by bishop on 9/7/16.
  */
class TradeActor @Inject() (@Named("candle-actor") candleActorRef: ActorRef) (implicit context: ExecutionContext) extends Actor with ActorLogging {
  import TradeActor._

  case class BuyRecord(date: DateTime, buyPrice: BigDecimal, amount: BigDecimal, last24BtcVolume: BigDecimal)

  // TODO track market volume as it happens how do you do this based upon the rolling 24 hour volume


  val marketWatch = scala.collection.mutable.ListBuffer.empty[String]
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
      // TODO keep track of the volume

    // this needs to receive both computed emas
    // TODO change the EMA actor update alorithm to fix this
    case MarketEMA(marketName, emaShort, emaLong) => {
      // are we already watching this market
      if (marketWatch.contains(marketName)) {
        // we are no longer interested in watching this market
        if (emaShort.ema < emaLong.ema) {
          marketWatch.remove(marketWatch.indexOf(marketName))
          // unsubscribe from market update for this
          // I need to know the time and current price to buy at
          //(candleActorRef ? GetLastestCandle(marketName)).mapTo[Option[MarketCandle]].map { opt =>
          //  opt match {
          //    case Some(candle) =>
          //      // sell here
          //      val percent = (candle.close - lastBuy) / lastBuy * 100
          //      val stats = s"$marketName (${new DateTime()}) sell: ${candle.close}, bought: $lastBuy, percent: ${percent.setScale(2)}"
          //      println(stats)
          //    case None =>
          //      println("Could not get latest candle?")
          //  }
          //}
        }
      } else  {
        if (emaShort.ema > emaLong.ema) {
          marketWatch.append(marketName)
          //(candleActorRef ? GetLastestCandle(marketName)).mapTo[Option[MarketCandle]].map { opt =>
          //  opt match {
          //    case Some(candle) =>
          //      lastBuy = candle.close
          //      println(s"$marketName: buy at $lastBuy (${new DateTime()})")
          //    case None =>
          //      println("why?")
          //  }
          //}
          // subscribe to market updates
          // need to know the time and current price
        }
      }
    }
  }
}
