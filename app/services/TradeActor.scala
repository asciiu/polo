package services

// external
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import javax.inject.{Inject, Named}

import akka.util.Timeout
import models.market.EMA
import models.poloniex.MarketCandle
import org.joda.time.DateTime
import services.CandleManagerActor.GetLastestCandle

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

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

  val marketWatch = scala.collection.mutable.ListBuffer.empty[String]
  val eventBus = PoloniexEventBus()
  var lastBuy: BigDecimal = 0

  implicit val timeout = Timeout(5 seconds)

  override def preStart() = {
    // receive messages from exponential moving average
    eventBus.subscribe(self, "/ema/update")
  }

  override def postStop() = {
    eventBus.unsubscribe(self, "/ema/update")
  }

  def receive = {
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
          (candleActorRef ? GetLastestCandle(marketName)).mapTo[Option[MarketCandle]].map { opt =>
            opt match {
              case Some(candle) =>
                // sell here
                log.info(s"$marketName: sell at ${candle.close} (${new DateTime()})")
                val percent = (candle.close - lastBuy) / lastBuy
                log.info(s"   gained $percent")
              case None =>
            }
          }
        }
      } else  {
        if (emaShort.ema > emaLong.ema) {
          marketWatch.append(marketName)
          println("I need to buy NOW!")
          (candleActorRef ? GetLastestCandle(marketName)).mapTo[Option[MarketCandle]].map { opt =>
            opt match {
              case Some(candle) =>
                lastBuy = candle.close
                log.info(s"$marketName: buy at $lastBuy (${new DateTime()})")
              case None =>
                log.info("why?")
            }
          }
          // subscribe to market updates
          // need to know the time and current price
        }
      }
    }
  }
}
