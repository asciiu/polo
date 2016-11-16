package controllers

import javax.inject.{Inject, Singleton}

import jp.t2v.lab.play2.auth.OptionalAuthElement
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import play.api.routing.JavaScriptReverseRouter
import services.DBService

@Singleton
class Application @Inject()(val database: DBService,
                            val messagesApi: MessagesApi,
                            implicit val webJarAssets: WebJarAssets)
  extends Controller with AuthConfigTrait with OptionalAuthElement with I18nSupport  {

  def index() = StackAction { implicit request =>
    Ok(views.html.index(loggedIn))
  }

  def javascriptRoutes() = Action { implicit request =>
    import routes.javascript._

    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        PoloniexController.candles,
        PoloniexController.messages,
        PoloniexController.updates,
        PoloniexController.setup,
        HistoryController.socket
      )
    ).as("text/javascript")
  }
}
