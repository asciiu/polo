package models.account

import java.util.UUID
import org.joda.time.DateTime
import scala.concurrent.Future

case class MailTokenUser(id: String, email: String, expirationTime: DateTime, isSignUp: Boolean)

object MailTokenUser {
  def apply(email: String, isSignUp: Boolean): MailTokenUser =
    MailTokenUser(UUID.randomUUID().toString, email, (new DateTime()).plusHours(24), isSignUp)

  // TODO persist these tokens
  val tokens = scala.collection.mutable.HashMap[String, MailTokenUser]()

  def findById(id: String): Future[Option[MailTokenUser]] = {
    Future.successful(tokens.get(id))
  }

  def save(token: MailTokenUser): Future[MailTokenUser] = {
    tokens += (token.id -> token)
    Future.successful(token)
  }

  def delete(id: String): Unit = {
    tokens.remove(id)
  }
}