package models.db

// AUTO-GENERATED Slick data model [2016-10-26T15:05:25.131-06:00[America/Denver]]

/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = utils.db.TetraoPostgresDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: utils.db.TetraoPostgresDriver
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(Account.schema, Message.schema, PoloniexCandle.schema, PoloniexMessage.schema, PoloniexOrders.schema, PoloniexSessions.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Account
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param name Database column name SqlType(text)
   *  @param email Database column email SqlType(text)
   *  @param emailConfirmed Database column email_confirmed SqlType(bool)
   *  @param password Database column password SqlType(text)
   *  @param role Database column role SqlType(user_role)
   *  @param createdAt Database column created_at SqlType(timestamptz)
   *  @param updatedAt Database column updated_at SqlType(timestamptz) */
  case class AccountRow(id: Int, name: String, email: String, emailConfirmed: Boolean, password: String, role: models.db.AccountRole.Value, createdAt: java.time.OffsetDateTime, updatedAt: java.time.OffsetDateTime)
  /** GetResult implicit for fetching AccountRow objects using plain SQL queries */
  implicit def GetResultAccountRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Boolean], e3: GR[models.db.AccountRole.Value], e4: GR[java.time.OffsetDateTime]): GR[AccountRow] = GR{
    prs => import prs._
    AccountRow.tupled((<<[Int], <<[String], <<[String], <<[Boolean], <<[String], <<[models.db.AccountRole.Value], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Account(_tableTag: Tag) extends Table[AccountRow](_tableTag, "users") {
    def * = (id, name, email, emailConfirmed, password, role, createdAt, updatedAt) <> (AccountRow.tupled, AccountRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(name), Rep.Some(email), Rep.Some(emailConfirmed), Rep.Some(password), Rep.Some(role), Rep.Some(createdAt), Rep.Some(updatedAt)).shaped.<>({r=>import r._; _1.map(_=> AccountRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column name SqlType(text) */
    val name: Rep[String] = column[String]("name")
    /** Database column email SqlType(text) */
    val email: Rep[String] = column[String]("email")
    /** Database column email_confirmed SqlType(bool) */
    val emailConfirmed: Rep[Boolean] = column[Boolean]("email_confirmed")
    /** Database column password SqlType(text) */
    val password: Rep[String] = column[String]("password")
    /** Database column role SqlType(user_role) */
    val role: Rep[models.db.AccountRole.Value] = column[models.db.AccountRole.Value]("role")
    /** Database column created_at SqlType(timestamptz) */
    val createdAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("created_at")
    /** Database column updated_at SqlType(timestamptz) */
    val updatedAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("updated_at")

    /** Uniqueness Index over (email) (database name users_email_key) */
    val index1 = index("users_email_key", email, unique=true)
  }
  /** Collection-like TableQuery object for table Account */
  lazy val Account = new TableQuery(tag => new Account(tag))

  /** Entity class storing rows of table Message
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param content Database column content SqlType(text)
   *  @param tagList Database column tag_list SqlType(_text), Length(2147483647,false)
   *  @param createdAt Database column created_at SqlType(timestamptz)
   *  @param updatedAt Database column updated_at SqlType(timestamptz) */
  case class MessageRow(id: Int, content: String, tagList: List[String], createdAt: java.time.OffsetDateTime, updatedAt: java.time.OffsetDateTime)
  /** GetResult implicit for fetching MessageRow objects using plain SQL queries */
  implicit def GetResultMessageRow(implicit e0: GR[Int], e1: GR[String], e2: GR[List[String]], e3: GR[java.time.OffsetDateTime]): GR[MessageRow] = GR{
    prs => import prs._
    MessageRow.tupled((<<[Int], <<[String], <<[List[String]], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table message. Objects of this class serve as prototypes for rows in queries. */
  class Message(_tableTag: Tag) extends Table[MessageRow](_tableTag, "message") {
    def * = (id, content, tagList, createdAt, updatedAt) <> (MessageRow.tupled, MessageRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(content), Rep.Some(tagList), Rep.Some(createdAt), Rep.Some(updatedAt)).shaped.<>({r=>import r._; _1.map(_=> MessageRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column content SqlType(text) */
    val content: Rep[String] = column[String]("content")
    /** Database column tag_list SqlType(_text), Length(2147483647,false) */
    val tagList: Rep[List[String]] = column[List[String]]("tag_list", O.Length(2147483647,varying=false))
    /** Database column created_at SqlType(timestamptz) */
    val createdAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("created_at")
    /** Database column updated_at SqlType(timestamptz) */
    val updatedAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("updated_at")
  }
  /** Collection-like TableQuery object for table Message */
  lazy val Message = new TableQuery(tag => new Message(tag))

  /** Entity class storing rows of table PoloniexCandle
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param sessionId Database column session_id SqlType(int4)
   *  @param cryptoCurrency Database column crypto_currency SqlType(text)
   *  @param open Database column open SqlType(numeric)
   *  @param close Database column close SqlType(numeric)
   *  @param lowestAsk Database column lowest_ask SqlType(numeric)
   *  @param highestBid Database column highest_bid SqlType(numeric)
   *  @param createdAt Database column created_at SqlType(timestamptz) */
  case class PoloniexCandleRow(id: Int, sessionId: Int, cryptoCurrency: String, open: scala.math.BigDecimal, close: scala.math.BigDecimal, lowestAsk: scala.math.BigDecimal, highestBid: scala.math.BigDecimal, createdAt: java.time.OffsetDateTime)
  /** GetResult implicit for fetching PoloniexCandleRow objects using plain SQL queries */
  implicit def GetResultPoloniexCandleRow(implicit e0: GR[Int], e1: GR[String], e2: GR[scala.math.BigDecimal], e3: GR[java.time.OffsetDateTime]): GR[PoloniexCandleRow] = GR{
    prs => import prs._
    PoloniexCandleRow.tupled((<<[Int], <<[Int], <<[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table poloniex_candles. Objects of this class serve as prototypes for rows in queries. */
  class PoloniexCandle(_tableTag: Tag) extends Table[PoloniexCandleRow](_tableTag, "poloniex_candles") {
    def * = (id, sessionId, cryptoCurrency, open, close, lowestAsk, highestBid, createdAt) <> (PoloniexCandleRow.tupled, PoloniexCandleRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(sessionId), Rep.Some(cryptoCurrency), Rep.Some(open), Rep.Some(close), Rep.Some(lowestAsk), Rep.Some(highestBid), Rep.Some(createdAt)).shaped.<>({r=>import r._; _1.map(_=> PoloniexCandleRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column session_id SqlType(int4) */
    val sessionId: Rep[Int] = column[Int]("session_id")
    /** Database column crypto_currency SqlType(text) */
    val cryptoCurrency: Rep[String] = column[String]("crypto_currency")
    /** Database column open SqlType(numeric) */
    val open: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("open")
    /** Database column close SqlType(numeric) */
    val close: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("close")
    /** Database column lowest_ask SqlType(numeric) */
    val lowestAsk: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("lowest_ask")
    /** Database column highest_bid SqlType(numeric) */
    val highestBid: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("highest_bid")
    /** Database column created_at SqlType(timestamptz) */
    val createdAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("created_at")

    /** Foreign key referencing PoloniexSessions (database name poloniex_candles_session_id_fkey) */
    lazy val poloniexSessionsFk = foreignKey("poloniex_candles_session_id_fkey", sessionId, PoloniexSessions)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table PoloniexCandle */
  lazy val PoloniexCandle = new TableQuery(tag => new PoloniexCandle(tag))

  /** Entity class storing rows of table PoloniexMessage
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param sessionId Database column session_id SqlType(int4)
   *  @param cryptoCurrency Database column crypto_currency SqlType(text)
   *  @param last Database column last SqlType(numeric)
   *  @param lowestAsk Database column lowest_ask SqlType(numeric)
   *  @param highestBid Database column highest_bid SqlType(numeric)
   *  @param percentChange Database column percent_change SqlType(numeric)
   *  @param baseVolume Database column base_volume SqlType(numeric)
   *  @param quoteVolume Database column quote_volume SqlType(numeric)
   *  @param isFrozen Database column is_frozen SqlType(bool)
   *  @param high24hr Database column high_24hr SqlType(numeric)
   *  @param low24hr Database column low_24hr SqlType(numeric)
   *  @param createdAt Database column created_at SqlType(timestamptz)
   *  @param updatedAt Database column updated_at SqlType(timestamptz) */
  case class PoloniexMessageRow(id: Int, sessionId: Int, cryptoCurrency: String, last: scala.math.BigDecimal, lowestAsk: scala.math.BigDecimal, highestBid: scala.math.BigDecimal, percentChange: scala.math.BigDecimal, baseVolume: scala.math.BigDecimal, quoteVolume: scala.math.BigDecimal, isFrozen: Boolean, high24hr: scala.math.BigDecimal, low24hr: scala.math.BigDecimal, createdAt: java.time.OffsetDateTime, updatedAt: java.time.OffsetDateTime)
  /** GetResult implicit for fetching PoloniexMessageRow objects using plain SQL queries */
  implicit def GetResultPoloniexMessageRow(implicit e0: GR[Int], e1: GR[String], e2: GR[scala.math.BigDecimal], e3: GR[Boolean], e4: GR[java.time.OffsetDateTime]): GR[PoloniexMessageRow] = GR{
    prs => import prs._
    PoloniexMessageRow.tupled((<<[Int], <<[Int], <<[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[Boolean], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table poloniex_messages. Objects of this class serve as prototypes for rows in queries. */
  class PoloniexMessage(_tableTag: Tag) extends Table[PoloniexMessageRow](_tableTag, "poloniex_messages") {
    def * = (id, sessionId, cryptoCurrency, last, lowestAsk, highestBid, percentChange, baseVolume, quoteVolume, isFrozen, high24hr, low24hr, createdAt, updatedAt) <> (PoloniexMessageRow.tupled, PoloniexMessageRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(sessionId), Rep.Some(cryptoCurrency), Rep.Some(last), Rep.Some(lowestAsk), Rep.Some(highestBid), Rep.Some(percentChange), Rep.Some(baseVolume), Rep.Some(quoteVolume), Rep.Some(isFrozen), Rep.Some(high24hr), Rep.Some(low24hr), Rep.Some(createdAt), Rep.Some(updatedAt)).shaped.<>({r=>import r._; _1.map(_=> PoloniexMessageRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10.get, _11.get, _12.get, _13.get, _14.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column session_id SqlType(int4) */
    val sessionId: Rep[Int] = column[Int]("session_id")
    /** Database column crypto_currency SqlType(text) */
    val cryptoCurrency: Rep[String] = column[String]("crypto_currency")
    /** Database column last SqlType(numeric) */
    val last: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("last")
    /** Database column lowest_ask SqlType(numeric) */
    val lowestAsk: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("lowest_ask")
    /** Database column highest_bid SqlType(numeric) */
    val highestBid: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("highest_bid")
    /** Database column percent_change SqlType(numeric) */
    val percentChange: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("percent_change")
    /** Database column base_volume SqlType(numeric) */
    val baseVolume: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("base_volume")
    /** Database column quote_volume SqlType(numeric) */
    val quoteVolume: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("quote_volume")
    /** Database column is_frozen SqlType(bool) */
    val isFrozen: Rep[Boolean] = column[Boolean]("is_frozen")
    /** Database column high_24hr SqlType(numeric) */
    val high24hr: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("high_24hr")
    /** Database column low_24hr SqlType(numeric) */
    val low24hr: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("low_24hr")
    /** Database column created_at SqlType(timestamptz) */
    val createdAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("created_at")
    /** Database column updated_at SqlType(timestamptz) */
    val updatedAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("updated_at")

    /** Foreign key referencing PoloniexSessions (database name poloniex_messages_session_id_fkey) */
    lazy val poloniexSessionsFk = foreignKey("poloniex_messages_session_id_fkey", sessionId, PoloniexSessions)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table PoloniexMessage */
  lazy val PoloniexMessage = new TableQuery(tag => new PoloniexMessage(tag))

  /** Entity class storing rows of table PoloniexOrders
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param cryptoCurrency Database column crypto_currency SqlType(text)
   *  @param price Database column price SqlType(numeric)
   *  @param quantity Database column quantity SqlType(numeric)
   *  @param btcTotal Database column btc_total SqlType(numeric)
   *  @param orderType Database column order_type SqlType(order_type)
   *  @param createdAt Database column created_at SqlType(timestamptz)
   *  @param updatedAt Database column updated_at SqlType(timestamptz) */
  case class PoloniexOrdersRow(id: Int, cryptoCurrency: String, price: scala.math.BigDecimal, quantity: scala.math.BigDecimal, btcTotal: scala.math.BigDecimal, orderType: models.db.OrderType.Value, createdAt: java.time.OffsetDateTime, updatedAt: java.time.OffsetDateTime)
  /** GetResult implicit for fetching PoloniexOrdersRow objects using plain SQL queries */
  implicit def GetResultPoloniexOrdersRow(implicit e0: GR[Int], e1: GR[String], e2: GR[scala.math.BigDecimal], e3: GR[models.db.OrderType.Value], e4: GR[java.time.OffsetDateTime]): GR[PoloniexOrdersRow] = GR{
    prs => import prs._
    PoloniexOrdersRow.tupled((<<[Int], <<[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[models.db.OrderType.Value], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table poloniex_orders. Objects of this class serve as prototypes for rows in queries. */
  class PoloniexOrders(_tableTag: Tag) extends Table[PoloniexOrdersRow](_tableTag, "poloniex_orders") {
    def * = (id, cryptoCurrency, price, quantity, btcTotal, orderType, createdAt, updatedAt) <> (PoloniexOrdersRow.tupled, PoloniexOrdersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(cryptoCurrency), Rep.Some(price), Rep.Some(quantity), Rep.Some(btcTotal), Rep.Some(orderType), Rep.Some(createdAt), Rep.Some(updatedAt)).shaped.<>({r=>import r._; _1.map(_=> PoloniexOrdersRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column crypto_currency SqlType(text) */
    val cryptoCurrency: Rep[String] = column[String]("crypto_currency")
    /** Database column price SqlType(numeric) */
    val price: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("price")
    /** Database column quantity SqlType(numeric) */
    val quantity: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("quantity")
    /** Database column btc_total SqlType(numeric) */
    val btcTotal: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("btc_total")
    /** Database column order_type SqlType(order_type) */
    val orderType: Rep[models.db.OrderType.Value] = column[models.db.OrderType.Value]("order_type")
    /** Database column created_at SqlType(timestamptz) */
    val createdAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("created_at")
    /** Database column updated_at SqlType(timestamptz) */
    val updatedAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("updated_at")
  }
  /** Collection-like TableQuery object for table PoloniexOrders */
  lazy val PoloniexOrders = new TableQuery(tag => new PoloniexOrders(tag))

  /** Entity class storing rows of table PoloniexSessions
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param notes Database column notes SqlType(text), Default(None)
   *  @param startedAt Database column started_at SqlType(timestamptz)
   *  @param endedAt Database column ended_at SqlType(timestamptz), Default(None) */
  case class PoloniexSessionsRow(id: Int, notes: Option[String] = None, startedAt: java.time.OffsetDateTime, endedAt: Option[java.time.OffsetDateTime] = None)
  /** GetResult implicit for fetching PoloniexSessionsRow objects using plain SQL queries */
  implicit def GetResultPoloniexSessionsRow(implicit e0: GR[Int], e1: GR[Option[String]], e2: GR[java.time.OffsetDateTime], e3: GR[Option[java.time.OffsetDateTime]]): GR[PoloniexSessionsRow] = GR{
    prs => import prs._
    PoloniexSessionsRow.tupled((<<[Int], <<?[String], <<[java.time.OffsetDateTime], <<?[java.time.OffsetDateTime]))
  }
  /** Table description of table poloniex_sessions. Objects of this class serve as prototypes for rows in queries. */
  class PoloniexSessions(_tableTag: Tag) extends Table[PoloniexSessionsRow](_tableTag, "poloniex_sessions") {
    def * = (id, notes, startedAt, endedAt) <> (PoloniexSessionsRow.tupled, PoloniexSessionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), notes, Rep.Some(startedAt), endedAt).shaped.<>({r=>import r._; _1.map(_=> PoloniexSessionsRow.tupled((_1.get, _2, _3.get, _4)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column notes SqlType(text), Default(None) */
    val notes: Rep[Option[String]] = column[Option[String]]("notes", O.Default(None))
    /** Database column started_at SqlType(timestamptz) */
    val startedAt: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("started_at")
    /** Database column ended_at SqlType(timestamptz), Default(None) */
    val endedAt: Rep[Option[java.time.OffsetDateTime]] = column[Option[java.time.OffsetDateTime]]("ended_at", O.Default(None))
  }
  /** Collection-like TableQuery object for table PoloniexSessions */
  lazy val PoloniexSessions = new TableQuery(tag => new PoloniexSessions(tag))
}
