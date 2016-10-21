package controllers

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import jp.t2v.lab.play2.auth.AuthElement
import models.db.{AccountRole, Tables}
import models.db.Tables._
import models.db.Tables.profile.api._
import models.market.MarketStructures.PeriodVolume
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.Controller
import services.DBService

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import views.html.{history => view}

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
                                    conf: Configuration)
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

    database.runAsync(marketCandles.result).map { candles =>


      val l = candles.map { c =>

        Json.arr(
          // TODO UTF offerset should come from client
          // I've subtracted 6 hours(2.16e+7 milliseconds) for denver time for now
          c.createdAt.toEpochSecond() * 1000L - 2.16e+7,
          c.open,
          c.highestBid,
          c.lowestAsk,
          c.close,
          0,
          0,
          0
        )
      }

      Ok(Json.toJson(l))
    }
  }
}
