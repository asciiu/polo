package controllers

import javax.inject.{Inject, Named}

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import jp.t2v.lab.play2.auth.AuthElement
import models.db.{AccountRole, Tables}
import play.api.Configuration
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import services.DBService

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions


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
class PlaybackController  @Inject()(val database: DBService,
                                    val messagesApi: MessagesApi,
                                    conf: Configuration)
                                   (implicit system: ActorSystem,
                                    materializer: Materializer,
                                    context: ExecutionContext,
                                    webJarAssets: WebJarAssets)

  extends Controller with AuthConfigTrait with AuthElement with I18nSupport {

  /**
    * Reads captured poloniex data from the DB and replays it in a test trading scenario.
    * @return
    */
  def playback() = AsyncStack(AuthorityKey -> AccountRole.normal) { implicit request =>
    Future.successful(Ok("playback info here"))
  }
}
