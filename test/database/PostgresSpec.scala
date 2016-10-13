package database

import java.io.File

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Suite}
import utils.db.TetraoPostgresDriver

/**
  * Setups up a slick connection to the DB defined in application.conf.
  */
trait PostgresSpec extends Suite with BeforeAndAfterAll {
  val conf = ConfigFactory.parseFile(new File("conf/application.conf"))
  val databaseURL = conf.getString("slick.dbs.default.db.url")
  val databaseUser = conf.getString("slick.dbs.default.db.user")
  val databasePassword = conf.getString("slick.dbs.default.db.password")
  val jdbcDriver = conf.getString("slick.dbs.default.driver")

  val database = TetraoPostgresDriver.api.Database.forURL(
    url = databaseURL,
    driver = jdbcDriver,
    user = databaseUser,
    password = databasePassword
  )

  override def afterAll(): Unit = {
    database.shutdown
  }
}
