import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent._
import javax.inject.Singleton

import com.google.inject.Inject
import controllers.WebJarAssets
import play.api.i18n.{I18nSupport, MessagesApi}

@Singleton
class ErrorHandler @Inject() (val messagesApi: MessagesApi)(implicit assets: WebJarAssets) extends HttpErrorHandler with I18nSupport {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    Future.successful(
      if(statusCode == play.api.http.Status.NOT_FOUND) {
        Ok(views.html.errors.notFound(request))
      } else {
        Status(statusCode)("A client error occurred: " + message)
      }
    )
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    Future.successful(
      InternalServerError("A server error occurred: " + exception.getMessage)
    )
  }
}