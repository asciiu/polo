package utils.poloniex

import models.poloniex.CurrencyBalance
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.libs.functional.syntax._

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by bishop on 9/7/16.
  */
class PoloniexTradeClient (apiKey: String,
                           secretKey: String,
                           conf: Configuration,
                           wsClient: WSClient)
                          (implicit context: ExecutionContext) extends PoloniexAuth(apiKey, secretKey) {

  val url = conf.getString("poloniex.url.trade").getOrElse("https://poloniex.com/tradingApi")

  private def nonce = System.currentTimeMillis().toInt.toString
  private def params(command: String, nonce: String) = s"command=$command&nonce=$nonce"

  /**
    * Returns all of your available balances.
    */
  def availableBlalances(): Unit = {
    val n = nonce
    val command = "returnBalances"

    setAuthenticationHeaders(wsClient.url(url), params(command, n))
      .post(Map("command" -> Seq(command), "nonce" -> Seq(n)))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
    }
  }

  private val balanceReads: Reads[(BigDecimal, BigDecimal, BigDecimal)] = (
    (JsPath \ "available").read[BigDecimal] and
      (JsPath \ "onOrders").read[BigDecimal] and
      (JsPath \ "btcValue").read[BigDecimal]
      tupled
    )

  private implicit val marketRead: Reads[List[CurrencyBalance]] = Reads( js =>
    JsSuccess(js.as[JsObject].fieldSet.map { currency =>
      val balance = currency._2.as[(BigDecimal, BigDecimal, BigDecimal)](balanceReads)
      CurrencyBalance(currency._1, balance._1, balance._2, balance._3)
    }.toList))

  /**
    * Returns all of your balances, including available balance, balance on orders,
    * and the estimated BTC value of your balance.
    * {"LTC":{"available":"5.015","onOrders":"1.0025","btcValue":"0.078"},"NXT:{...} ... }
    */
  def completeBalances(): Future[List[CurrencyBalance]] = {
    val n = nonce
    val command = "returnCompleteBalances"

    setAuthenticationHeaders(wsClient.url(url), params(command, n))
      .post(Map("command" -> Seq(command), "nonce" -> Seq(n)))
      .map{ response =>
        response.json.validate[List[CurrencyBalance]] match {
          case s: JsSuccess[List[CurrencyBalance]] => s.get
          case e: JsError => List[CurrencyBalance]()
        }
      }
  }

  /**
    * Returns your open orders for a given market
    */
  def openOrders(): Unit = {
    val n = nonce
    val command = "returnOpenOrders"
    val postParams = params(command, n) + "&currencyPair=all"

    setAuthenticationHeaders(wsClient.url(url), postParams)
      .post(Map("command" -> Seq(command), "nonce" -> Seq(n), "currencyPair" -> Seq("all")))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }

  /**
    * Returns all trades involving a given order, specified by the "orderNumber" POST parameter.
    * If no trades for the order have occurred or you specify an order that does not belong to you,
    * you will receive an error.
    *
    * @param orderNumber
    */
  def orderTrades(orderNumber: Int): Unit = {
    val n = nonce
    val command = "returnOrderTrades"
    val postParams = params(command, n) + s"&orderNumber=${orderNumber}"

    setAuthenticationHeaders(wsClient.url(url), postParams)
      .post(Map("command" -> Seq(command), "nonce" -> Seq(n), "orderNumber" -> Seq(orderNumber.toString)))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }

  /**
    * Places a limit buy order in a given market. If successful, the method will return the order number
    *
    * @param currencyPair
    * @param rate
    * @param amount
    */
  def buy(currencyPair: String, rate: BigDecimal, amount: BigDecimal): Unit = {
    val n = nonce
    val command = "buy"
    val postParams = params(command, n) +
      s"&currencyPair=$currencyPair&rate=$rate&amount=$amount"

    setAuthenticationHeaders(wsClient.url(url), postParams)
      .post(Map("command" -> Seq(command),
        "nonce" -> Seq(n),
        "currencyPair" -> Seq(currencyPair),
        "rate" -> Seq(rate.toString),
        "amount" -> Seq(amount.toString)
      ))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }

  /**
    * Places a sell order in a given market.
    *
    * @param currencyPair
    * @param rate
    * @param amount
    */
  def sell(currencyPair: String, rate: BigDecimal, amount: BigDecimal): Unit = {
    val n = nonce
    val command = "sell"
    val postParams = params(command, n) +
      s"&currencyPair=$currencyPair&rate=$rate&amount=$amount"

    setAuthenticationHeaders(wsClient.url(url), postParams)
      .post(Map("command" -> Seq(command),
        "nonce" -> Seq(n),
        "currencyPair" -> Seq(currencyPair),
        "rate" -> Seq(rate.toString),
        "amount" -> Seq(amount.toString)
      ))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }

  /**
    * Cancels an order and places a new one of the same type in a single atomic transaction,
    * meaning either both operations will succeed or both will fail.
    *
    * @param orderNumber
    * @param rate
    * @param amount
    */
  def moveOrder(orderNumber: Long, rate: BigDecimal, amount: BigDecimal): Unit = {
    val n = nonce
    val command = "moveOrder"
    val postParams = params(command, n) +
      s"&orderNumber=$orderNumber&rate=$rate&amount=$amount"

    setAuthenticationHeaders(wsClient.url(url), postParams)
      .post(Map("command" -> Seq(command),
        "nonce" -> Seq(n),
        "orderNumber" -> Seq(orderNumber.toString),
        "rate" -> Seq(rate.toString),
        "amount" -> Seq(amount.toString)
      ))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }
}
