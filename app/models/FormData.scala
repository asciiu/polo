package models

import dao.UserDao
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.data.validation.{Constraint, Invalid, Valid}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class FormDataLogin(email: String, password: String)

case class FormDataAccount(name:String, email: String, password: String, passwordAgain:String)

object FormData {

  val login = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText
    )(FormDataLogin.apply)(FormDataLogin.unapply)
  )

  val addMessage = Form(
    mapping(
      "content" -> nonEmptyText,
      "tags" -> text
    )(Message.formApply)(Message.formUnapply)
  )

  val uniqueEmail = Constraint[String] { email: String =>
    val userFuture = UserDao.findByEmail(email)

    Await.result(userFuture, Duration.Inf) match {
      case Some(user) => Invalid("email already taken")
      case None => Valid
    }
  }

  private[this] def accountForm(passwordMapping:Mapping[String]) = Form(
    mapping(
      "name" -> nonEmptyText,
      "email" -> email.verifying(maxLength(250), uniqueEmail),
      "password" -> passwordMapping,
      "passwordAgain" -> passwordMapping
    )(FormDataAccount.apply)(FormDataAccount.unapply)
  )

  val updateAccount = accountForm(text)

  val addAccount = accountForm(nonEmptyText)
}
