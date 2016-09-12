package services

import akka.actor.ActorSystem
import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer._
import scala.concurrent.duration._
import scala.language.postfixOps


// internals
import utils.ConfigSupport

@ImplementedBy(classOf[MailServiceImpl])
trait MailService {
  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit
  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String): Unit
}

class MailServiceImpl @Inject() (mailerClient: MailerClient,
                                 system: ActorSystem,
                                 val conf: Configuration) extends MailService with ConfigSupport {
	lazy val from = confRequiredString("play.mailer.from")

  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: String, bodyText: String) = {
    system.scheduler.scheduleOnce(100 milliseconds) {
      sendEmail(recipients: _*)(subject, bodyHtml, bodyText)
    }
  }

  def sendEmail(recipients: String*)(subject: String, bodyHtml: String, bodyText: String) =
    mailerClient.send(Email(subject, from, recipients, Some(bodyText), Some(bodyHtml)))
}