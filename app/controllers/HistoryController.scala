package controllers

// external
import akka.pattern.ask
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.util.Timeout
import jp.t2v.lab.play2.auth.AuthElement
import javax.inject.{Inject, Named}

import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Controller, WebSocket}
import services.actors.PlaybackService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.math.BigDecimal.RoundingMode

// internal
import models.db.AccountRole
import models.db.Tables._
import models.db.Tables.profile.api._
import models.market.MarketCandle
import models.market.MarketStructures.{ExponentialMovingAverage, MarketMessage}
import models.poloniex.PoloniexEventBus
import services.actors.PoloniexMarketService.SetCandles
import services.DBService
import views.html.{history => view}

/**
  * All R&D projects should start here.
  *
  * @param database
  * @param messagesApi
  * @param conf
  * @param system
  * @param materializer
  * @param context
  * @param webJarAssets
  */
class HistoryController  @Inject()(val database: DBService,
                                   val messagesApi: MessagesApi,
                                   conf: Configuration)
                                   (implicit system: ActorSystem,
                                    materializer: Materializer,
                                    context: ExecutionContext,
                                    webJarAssets: WebJarAssets)

  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  /**
    * Sends market updates to all connected clients.
    */
  def socket(sessionId: Int) = WebSocket.accept[String, String] { request =>
    // each client will be served by this actor
    ActorFlow.actorRef(out => PlaybackService.props(out, database, sessionId))
  }
  /**
    * Reads captured poloniex data from the DB and replays it in a test trading scenario.
    *
    * @return
    */
  def markets(sessionId: Int) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    val marketNames = PoloniexMessage
      .filter( r => r.sessionId === sessionId && r.cryptoCurrency.startsWith("BTC_"))
      .groupBy(_.cryptoCurrency).map(_._1)

    database.runAsync(marketNames.result).map { names =>
      Ok(view.markets(loggedIn, sessionId, names.sorted))
    }
  }

  def capturedSessions() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    Future.successful(Ok(view.markets(loggedIn, 1, List[String]())))
  }

//  def candles(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
//    val marketCandles = PoloniexCandle.filter( c => c.sessionId === sessionID && c.cryptoCurrency === marketName).sortBy(_.createdAt)
//
//    database.runAsync(marketCandles.result).flatMap{ candles =>
//      implicit val timeout = Timeout(5 seconds)
//
//      // TODO this should be controlled by socket session
//      val marketCandles = candles.map(c =>
//        new MarketCandle(c.createdAt, 5, c.open, c.close, c.highestBid, c.lowestAsk)).toList.reverse
//
//      (marketService ? SetCandles(marketName, marketCandles))
//        .mapTo[List[(Int, List[ExponentialMovingAverage])]]
//        .map { ema =>
//
//        val l = candles.map { c =>
//          val time = c.createdAt.toEpochSecond() * 1000L - 2.16e+7
//          val defaultEMA = ExponentialMovingAverage(c.createdAt, BigDecimal(0), 0)
//          Json.arr(
//            // TODO UTF offerset should come from client
//            // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
//            time,
//            c.open,
//            c.highestBid,
//            c.lowestAsk,
//            c.close,
//            ema(0)._2.find(avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema,
//            ema(1)._2.find(avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema,
//            0
//          )
//        }
//        Ok(Json.toJson(l))
//      }
//    }
//  }
}
