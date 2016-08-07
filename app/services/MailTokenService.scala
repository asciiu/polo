package services

// externals
import org.joda.time.DateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// internals
import models.account.MailTokenUser

trait MailToken {
  def id: String
  def email: String
  def expirationTime: DateTime
  def isExpired = expirationTime.isBeforeNow
}

trait MailTokenService[T <: MailToken] {
  def create(token: T): Future[Option[T]]
  def retrieve(id: String): Future[Option[T]]
  def consume(id: String): Unit
}

class MailTokenUserService extends MailTokenService[MailTokenUser] {
  def create(token: MailTokenUser): Future[Option[MailTokenUser]] = {
    MailTokenUser.save(token).map(Some(_))
  }
  def retrieve(id: String): Future[Option[MailTokenUser]] = {
    MailTokenUser.findById(id)
  }
  def consume(id: String): Unit = {
    MailTokenUser.delete(id)
  }
}
