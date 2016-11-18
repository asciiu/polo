package controllers

// external
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import akka.stream.Materializer
import javax.inject.{Inject, Named, Singleton}

import jp.t2v.lab.play2.auth.AuthElement
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{Controller, WebSocket}
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.Configuration

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.math.BigDecimal.RoundingMode

// internal
import models.market.MarketStructures.{MarketMessage => Msg}
import models.db.AccountRole
import models.poloniex.{PoloniexMarketUpdate, PoloniexMarketMessage}
import models.market.MarketStructures.MarketSetupNotification
import models.poloniex.PoloniexEventBus
import models.poloniex.trade.PoloniexTradeClient
import services.actors.MarketService.{ReturnAllData, ReturnLatestMessage}
import services.actors.MarketSocketService
import services.actors.NotificationService.GetMarketSetup
import services.DBService

@Singleton
class PoloniexController @Inject()(val database: DBService,
                                   val messagesApi: MessagesApi,
                                   ws: WSClient,
                                   conf: Configuration,
                                   @Named("poloniex-orderbooks") orderBooks: ActorRef,
                                   @Named("poloniex-market") marketService: ActorRef,
                                   @Named("poloniex-feed") feedService: ActorRef,
                                   @Named("poloniex-alerts") notificationService: ActorRef)
                                  (implicit system: ActorSystem,
                                   materializer: Materializer,
                                   context: ExecutionContext,
                                   webJarAssets: WebJarAssets)
  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  implicit val marketStatus = Json.format[PoloniexMarketMessage]
  implicit val marketWrite = Json.writes[PoloniexMarketUpdate]
  implicit val marketRead: Reads[List[PoloniexMarketUpdate]] = Reads(js =>
    JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
      PoloniexMarketUpdate(ticker._1, ticker._2.as[PoloniexMarketMessage])
    }.toList))

  implicit val msgWrite = Json.writes[Msg]
  implicit val timeout = Timeout(5 seconds)

  val tradeClient = new PoloniexTradeClient(
    conf.getString("poloniex.apiKey").getOrElse(""),
    conf.getString("poloniex.secret").getOrElse(""),
    conf,
    ws
  )

  def isRecording() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    // TODO
    Future.successful(Ok(Json.toJson(false)))
  }

  def startCapture() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    // TODO
    Future.successful(Ok(Json.toJson(true)))
  }

  def endCapture() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    // TODO
    Future.successful(Ok(Json.toJson(true)))
  }

  /**
    * Directs the user to the details of the selected market.
    * @param marketName
    * @return
    */
  def market(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    // TODO this will fail when the market service at that name is not there
    val marketRef = system.actorSelection(s"akka://application/user/poloniex-market/$marketName")

    (marketRef ? ReturnLatestMessage).mapTo[Option[Msg]].map { msg =>
      msg match {
        case Some(m) =>
          // change percent format from decimal
          val percentChange = m.percentChange * 100
          val normalized = m.copy(percentChange =
            percentChange.setScale(2, RoundingMode.CEILING))

          Ok(views.html.poloniex.market(loggedIn, marketName, normalized))
        case None =>
          NotFound(views.html.errors.notFound(request))
      }
    }
  }

  /**
    * Displays all poloniex markets.
    */
  def markets() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    implicit val marketStatus = Json.reads[PoloniexMarketMessage]
    implicit val marketRead: Reads[List[PoloniexMarketUpdate]] = Reads(js =>
      JsSuccess(js.as[JsObject].fieldSet.map { ticker =>
        PoloniexMarketUpdate(ticker._1, ticker._2.as[PoloniexMarketMessage])
      }.toList))

    // TODO this request should be factored into to the poloniex models
    // poloniex markets
    val poloniexRequest: WSRequest = ws.url("https://poloniex.com/public?command=returnTicker")
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(10000.millis)

    poloniexRequest.get().map { polo =>
      polo.json.validate[List[PoloniexMarketUpdate]] match {
        case JsSuccess(tickers, t) =>
          // Bitcoin price
          val bitcoin = tickers.find( _.marketName == "USDT_BTC")

          // only care about btc markets
          // order by base volume - btc vol
          val btcmarkets = tickers.filter(t =>  t.marketName.startsWith("BTC"))
            .sortBy( tick => tick.info.baseVolume).reverse.map(t => {

            // change percent format from decimal
            val percentChange = t.info.percentChange * 100
            //val name = t.name.replace("BTC_", "")
            t.copy(info = t.info.copy(percentChange =
              percentChange.setScale(2, RoundingMode.CEILING)))
          })
          Ok(views.html.poloniex.markets(loggedIn, bitcoin, btcmarkets))
        case _ =>
          BadRequest("could not read poloniex market")
      }
    }
  }

  /**
    * Returns candle data for the following market.
    *
    * @param marketName
    */
  def candles(marketName: String) = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    // TODO this will fail if there is no actor there
    val marketRef = system.actorSelection(s"akka://application/user/poloniex-market/$marketName")
    (marketRef ? ReturnAllData).mapTo[List[JsArray]].map { data =>
      Ok(Json.toJson(data))
    }
  }

  /**
    * Forwards messages to connected clients.
    */
  def messages() = WebSocket.accept[String, String] { request =>
    class MarketsNotifier(out: ActorRef)(implicit ctx: ExecutionContext) extends Actor {
      val eventBus = PoloniexEventBus()
      override def preStart() = eventBus.subscribe(self, PoloniexEventBus.Updates)
      override def postStop() = eventBus.unsubscribe(self, PoloniexEventBus.Updates)
      def receive: Receive = {
        case msg: Msg =>
          val percentChange = msg.percentChange * 100
          val marketMessage = msg.copy(percentChange = percentChange.setScale(2, RoundingMode.CEILING))
          out ! Json.toJson(marketMessage).toString
      }
    }

    ActorFlow.actorRef(out => Props(new MarketsNotifier(out)))
  }

  /**
    * Sends market setups to all connected clients.
    */
  def setups() = WebSocket.accept[String, String] { request =>
    class SetupSubscriber(out: ActorRef)(implicit ctx: ExecutionContext) extends Actor {
      val eventBus = PoloniexEventBus()

      override def preStart() = {
        (notificationService ? GetMarketSetup).mapTo[Set[String]].map { markets =>
          val m = Json.toJson(markets)
          val json = Json.obj(
            "type" -> "MarketSetups",
            "data" -> m
          ).toString
          out ! json
        }
        eventBus.subscribe(self, PoloniexEventBus.BollingerNotification)
      }

      override def postStop() = {
        eventBus.unsubscribe(self, PoloniexEventBus.BollingerNotification)
      }

      def receive: Receive = {
        case MarketSetupNotification(marketName, isSetup) =>
          val json = Json.obj(
            "type" -> "MarketSetup",
            "data" -> Json.obj("marketName" -> marketName, "isSetup" -> isSetup)
          ).toString
          out ! json
      }
    }

    ActorFlow.actorRef(out => Props(new SetupSubscriber(out)))
  }

  /**
    * Sends market updates to all connected clients.
    * @param marketName
    */
  def updates(marketName: String) = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef(out => MarketSocketService.props(marketName, out, database))
  }
}
