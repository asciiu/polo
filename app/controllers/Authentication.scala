package controllers

// externals
import com.github.t3hnar.bcrypt.Password
import java.time.OffsetDateTime
import javax.inject.{Inject, Singleton}
import jp.t2v.lab.play2.auth._
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// internals
import models.account.MailTokenUser
import models.db.{AccountRole, Tables}
import models.User
import models.FormData
import services.{DBService, MailService}
import utils.db.TetraoPostgresDriver.api._
import utils.Mailer

@Singleton
class Authentication @Inject()(val database: DBService,
                               val messagesApi: MessagesApi,
                               val mailService: MailService,
                               implicit val webJarAssets: WebJarAssets) extends Controller with AuthConfigTrait with OptionalAuthElement with LoginLogout with I18nSupport {

  implicit val ms = mailService

  def prepareLogin() = StackAction { implicit request =>
    if (loggedIn.isDefined) {
      Redirect(routes.RestrictedApplication.messages())
    } else {
      Ok(views.html.login(FormData.login))
    }
  }

  def logout = Action.async { implicit request =>
    gotoLogoutSucceeded
  }

  def login() = Action.async { implicit request =>
    FormData.login.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.login(formWithErrors))),
      account => {
        val q = Tables.Account.filter { row =>
          row.email === account.email && row.emailConfirmed
        }

        database.runAsync(q.result.headOption).flatMap {
          case None => {
            Logger.warn(s"Wrong user")
            val form = FormData.login.fill(account).withError("email", "Invalid user")
            Future.successful(BadRequest(views.html.login(form)))
          }
          case Some(user) => {
            if(account.password.isBcrypted(user.password)) {
              Logger.info(s"Login by ${account.email} ${user}")
              gotoLoginSucceeded(user.id)
            } else {
              Logger.warn(s"Wrong login credentials!")
              val form = FormData.login.fill(account).withError("password", "Invalid password")
              Future.successful(BadRequest(views.html.login(form)))
            }
          }
        }
      }
    )
  }

  /**
    * Starts the sign up mechanism. It shows a form that the user have to fill in and submit.
    */
  def signup() = StackAction { implicit request =>
    Ok(views.html.signup(FormData.addAccount))
  }

  /**
    * Handles the form filled by the user. The user and its password are saved and it sends him an email
    * with a link to verify his email address.
    */
  def handleSignUp() = Action.async { implicit request =>
    FormData.addAccount.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.signup(formWithErrors))),
      accountFormData => {
        if(accountFormData.password.nonEmpty && accountFormData.password == accountFormData.passwordAgain) {
          val now = OffsetDateTime.now()
          val row = Tables.AccountRow(
            id = -1,
            name = accountFormData.name,
            email = accountFormData.email,
            emailConfirmed = false,
            password = accountFormData.password.bcrypt,
            role = AccountRole.normal,
            updatedAt = now,
            createdAt = now
          )

          database.runAsync((Tables.Account returning Tables.Account.map(_.id)) += row).map { id =>
            val token = MailTokenUser(accountFormData.email, isSignUp = true)
            val user = User(Some(id), accountFormData.name, accountFormData.email, false, accountFormData.password.bcrypt)
            Mailer.welcome(user, link = "somelinkhere")
            Ok(views.html.auth.almostSignedUp(accountFormData))
          }
        } else {
          val form = FormData.addAccount.fill(accountFormData).withError("passwordAgain", "Passwords don't match")
          Future.successful(BadRequest(views.html.signup(form)))
        }
      }
    )
  }
}
