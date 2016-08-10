package controllers

// externals
import jp.t2v.lab.play2.auth.{AuthConfig, CookieTokenAccessor}
import play.api.data.validation.{Constraint, Valid}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._

// internals
import models.{Account, Entity}
import models.db.{AccountRole, Tables}
import services.DBService
import utils.db.TetraoPostgresDriver.api._

private[controllers] trait AuthConfigTrait extends AuthConfig {

  /**
    * Reference to the database service to be run
    */
  def database: DBService

  /**
    * A type that is used to identify a user.
    * `String`, `Int`, `Long` and so on.
    */
  type Id = Int

  /**
    * A type that representss a user in your application.
    * `User`, `Account` and so on.
    */
  type User = Entity[Account]

  /**
    * A type that is defined by every action for authorization.
    */
  type Authority = AccountRole.Value

  /**
    * A `ClassTag` is used to retrieve an id from the Cache API.
    * Use something like this:
    */
  val idTag: ClassTag[Id] = classTag[Id]

  /**
    * The session timeout in seconds
    */
  val sessionTimeoutInSeconds: Int = 3600

  val uniqueEmail = Constraint[String] { email: String =>
    //val userFuture = UserDao.findByEmail(email)

    //Await.result(userFuture, Duration.Inf) match {
    //  case Some(user) => Invalid("auth.user.emailnotunique")
    //  case None => Valid
    //}
    Valid
  }


  //  def validate(password1: String, password2: Int) = {
  //    name match {
  //      case "bob" if age >= 18 =>
  //        Some(UserData(name, age))
  //      case "admin" =>
  //        Some(UserData(name, age))
  //      case _ =>
  //        None
  //    }
  //  }


  /**
    * A function that returns a `User` object from an `Id`.
    * You can alter the procedure to suit your application.
    */
  def resolveUser(id: Id)(implicit ctx: ExecutionContext): Future[Option[User]] = {
    database
      .runAsync(Tables.Account.filter(_.id === id).take(1).result.headOption)
      .map(_.map(models.Account(_)))
  }

  /**
    * Where to redirect the user after a successful login.
    */
  def loginSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.RestrictedApplication.messages()))
  }

  /**
    * Where to redirect the user after logging out
    */
  def logoutSucceeded(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.Application.index()))
  }

  /**
    * If the user is not logged in and tries to access a protected resource then redirect them as follows:
    */
  def authenticationFailed(request: RequestHeader)(implicit ctx: ExecutionContext): Future[Result] =
    Future.successful(Redirect(routes.Authentication.login()))

  /**
    * If authorization failed (usually incorrect password) redirect the user as follows:
    */
  override def authorizationFailed(request: RequestHeader, user: User, authority: Option[Authority])(implicit context: ExecutionContext): Future[Result] = {
    Future.successful(Redirect(routes.Authentication.login()))
  }

  /**
    * A function that determines what `Authority` a user has.
    * You should alter this procedure to suit your application.
    */
  def authorize(user: User, authority: Authority)(implicit ctx: ExecutionContext): Future[Boolean] = Future.successful {
    (user.data.role, authority) match {
      case (AccountRole.admin, _) => true
      case (AccountRole.normal, AccountRole.normal) => true
      case _ => false
    }
  }

  /**
    * (Optional)
    * You can custom SessionID Token handler.
    * Default implementation use Cookie.
    */
  override lazy val tokenAccessor = new CookieTokenAccessor(
    cookieSecureOption = false,
    cookieMaxAge = None
  )
}