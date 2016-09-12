package dao

// external
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// internal
import models.User

// TODO merge this with Entity and User models
// This needs to be DI if you're going to use it
object UserDao {
  val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)

  private class UsersTable(tag: Tag) extends Table[User](tag, "users") {
    // note you need autoinc here in order for the insert to work
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def emailConfirmed = column[Boolean]("email_confirmed")
    def password = column[String]("password")
    //def role = column[models.db.AccountRole.Value]("role")
    def * = (id.?, name, email, emailConfirmed, password) <>(User.tupled, User.unapply _)
  }

  private val users = TableQuery[UsersTable]

  //def all(): Future[Seq[User]] = dbConfig.db.run(users.result)

  def insert(user: User): Future[Long] = {
    dbConfig.db.run((users returning users.map(_.id)) += user).map { id =>
      id
    }
  }

  def update(user: User): Future[Option[User]] = {
    val query = for { u <- users if u.id === user.id } yield (u.email, u.emailConfirmed, u.password)
    dbConfig.db.run(query.update((user.email, user.emailConfirmed, user.password)).map{ num =>
      if (num > 0) Some(user)
      else None
    })
  }

  def findByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }
}







