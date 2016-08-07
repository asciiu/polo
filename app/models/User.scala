package models

import dao.UserDao
import scala.concurrent.Future

case class User(id: Option[Long] = None,
                name: String,
                email: String,
                emailConfirmed: Boolean,
                password: String
                )

//object User {
//  //def findByEmail(email: String): Future[Option[User]] = Future.successful(users.find(_._2.email == email).map(_._2))
//  def findByEmail(email: String): Future[Option[User]] = UserDao.findByEmail(email)
//  //	def findByEmailMap[A] (email: String)(f: User => A): Future[Option[A]] = findByEmail(email).map(_.map(f))
//
//  def save(user: User): Future[User] = {
//    UserDao.insert(user)
//    Future.successful(user)
//  }
//
//  def update(user: User): Future[Option[User]] = {
//    UserDao.update(user)
//  }
//
//  def remove(email: String): Future[Unit] = {
//    Future.successful()
//  }
//  //def save(user: User): Future[User] = {
//  //  // A rudimentary auto-increment feature...
//  //  def nextId: Long = users.maxBy(_._1)._1 + 1
//
//  //  val theUser = if (user.id.isDefined) user else user.copy(id = Some(nextId))
//  //  users += (theUser.id.get -> theUser)
  //  Future.successful(theUser)
  //}

  //def remove(email: String): Future[Unit] = findByEmail(email).map(_.map(u => users.remove(u.id.get)))
//}