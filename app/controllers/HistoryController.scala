package controllers

import javax.inject.{Inject, Named}

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.util.Timeout
import jp.t2v.lab.play2.auth.AuthElement
import models.db.{AccountRole, Tables}
import models.db.Tables._
import models.db.Tables.profile.api._
import models.market.MarketCandle
import models.market.MarketStructures.{Candles, ExponentialMovingAverage}
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.Controller
import services.DBService
import services.actors.PoloniexMarketService.{GetMovingAverages, SetCandles}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import views.html.{history => view}

import scala.concurrent.duration.Duration
import scala.math.BigDecimal.RoundingMode

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
                                   conf: Configuration,
                                   @Named("poloniex-history") marketService: ActorRef)
                                   (implicit system: ActorSystem,
                                    materializer: Materializer,
                                    context: ExecutionContext,
                                    webJarAssets: WebJarAssets)

  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  var sessionID = 23

  /**
    * Reads captured poloniex data from the DB and replays it in a test trading scenario.
    *
    * @return
    */
  def markets() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    val marketNames = PoloniexMessage
      .filter( r => r.sessionId === sessionID && r.cryptoCurrency.startsWith("BTC_"))
      .groupBy(_.cryptoCurrency).map(_._1)

    database.runAsync(marketNames.result).map { names =>
      Ok(view.markets(loggedIn, names.sorted))
    }
  }

  def capturedSessions() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    Future.successful(Ok(view.markets(loggedIn, List[String]())))
  }

  def candles(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    val marketCandles = PoloniexCandle.filter( c => c.sessionId === sessionID && c.cryptoCurrency === marketName).sortBy(_.createdAt)

    database.runAsync(marketCandles.result).flatMap{ candles =>
      implicit val timeout = Timeout(5 seconds)

      val marketCandles = candles.map(c =>
        new MarketCandle(c.createdAt, 5, c.open, c.close, c.highestBid, c.lowestAsk)).toList.reverse

      (marketService ? SetCandles(marketName, marketCandles))
        .mapTo[List[(Int, List[ExponentialMovingAverage])]]
        .map { ema =>

        val l = candles.map { c =>
          val time = c.createdAt.toEpochSecond() * 1000L - 2.16e+7
          val defaultEMA = ExponentialMovingAverage(c.createdAt, BigDecimal(0), 0)
          Json.arr(
            // TODO UTF offerset should come from client
            // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
            time,
            c.open,
            c.highestBid,
            c.lowestAsk,
            c.close,
            ema(0)._2.find(avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema,
            ema(1)._2.find(avg => c.createdAt.equals(avg.time)).getOrElse(defaultEMA).ema,
            0
          )
        }
        Ok(Json.toJson(l))
      }
    }
  }
}
