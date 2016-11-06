package services.actors

// external
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.contrib.pattern.ReceivePipeline
import java.time.OffsetDateTime

import models.analytics.individual.KitchenSink

import scala.concurrent.ExecutionContext

// internal
import models.analytics.Archiving
import models.market.MarketStructures._
import services.DBService

object MarketService {
  def props(marketName: String, database: DBService)(implicit context: ExecutionContext) =
    Props(new MarketService(marketName, database))

  case class SendCandles(out: ActorRef)
  case class SendLatestCandle(out: ActorRef)
  case class SendLatestMovingAverages(out: ActorRef)
  case class SendMovingAverages(out: ActorRef)
  case class SendVolume(out: ActorRef, time: OffsetDateTime)
  case class SendVolumes(out: ActorRef)
  case class SendLatestMessage(out: ActorRef)
  case class SendBollingerBands(out: ActorRef)
  case class SendLatestBollingerBands(out: ActorRef)
}

class MarketService(val marketName: String, val database: DBService) extends Actor
  with ActorLogging
  with ReceivePipeline
  with KitchenSink
  with Archiving {

  import MarketService._

  override def preStart() = {
    log.info(s"Started $marketName service")
  }

  override def postStop() = {
    log.info(s"Shutdown $marketName service")
  }

  def receive: Receive = {
    case msg: MarketMessage =>
      // TODO perform some strategy here

    case SendCandles(out) =>
      out ! getCandles()

    case SendLatestCandle(out) =>
      out ! getLatestCandle()

    case SendLatestMovingAverages(out) =>
      out ! getLatestMovingAverages()

    case SendMovingAverages(out) =>
      out ! getMovingAverages()

    case SendVolume(out, time) =>
      out ! getVolume(time)

    case SendVolumes(out) =>
      out ! getVolumes()

    case SendLatestMessage(out) =>
      out ! getLatestMessage()

    case SendBollingerBands(out) =>
      out ! getAllPoints()

    case SendLatestBollingerBands(out) =>
      out ! getLatestPoints()
  }
}

