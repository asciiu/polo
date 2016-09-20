package models.poloniex

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Hex
import play.api.libs.ws.WSRequest

/**
  * Created by bishop on 9/7/16.
  */
class PoloniexAuth(apiKey: String, secretKey: String) {

  protected def generateSignature(params: String): String = {
    val keyspec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA512")
    val shaMac = Mac.getInstance("HmacSHA512")
    shaMac.init(keyspec)

    val macData = shaMac.doFinal(params.getBytes())
    Hex.encodeHexString(macData)
  }

  def setAuthenticationHeaders(request: WSRequest,
                               params: String = ""): WSRequest = {

    val signature = generateSignature(params)

    request.withHeaders(
      "Key" -> apiKey,
      "Sign" -> signature)
  }

  def setNonAuthenticationHeaders(request: WSRequest) = {
    val gdaxRequest = request.withHeaders(
      "Accept" -> "application/json")
  }
}
