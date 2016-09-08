package utils.poloniex

import java.util.UUID
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext

/**
  * Created by bishop on 9/7/16.
  */
class PoloniexTradeClient (apiKey: String,
                           secretKey: String,
                           wsClient: WSClient)
                          (implicit context: ExecutionContext) extends PoloniexAuth(apiKey, secretKey) {
  // TODO read from conf
  val url = "https://poloniex.com/tradingApi"

  private def nonce = System.currentTimeMillis().toInt.toString
  private def params(command: String) = s"command=$command&nonce=$nonce"

  def balances(): Unit = {
    val command = "returnBalances"
    val signature = generateSignature(params(command))

    setAuthenticationHeaders(wsClient.url(url), params(command))
      .post(Map("command" -> Seq(command), "nonce" -> Seq(nonce)))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
    }
  }

  def completeBalances(): Unit = {
    val command = "returnCompleteBalances"
    val signature = generateSignature(params(command))

    setAuthenticationHeaders(wsClient.url(url), params(command))
      .post(Map("command" -> Seq(command), "nonce" -> Seq(nonce)))
      .map{ response =>
        println(s"HERE ${response.json.toString()}")
      }
  }

  def cancelOrder(orderId: UUID): Unit = {
  }
}
