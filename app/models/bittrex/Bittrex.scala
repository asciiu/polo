package models.bittrex

import org.joda.time.DateTime
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.functional.syntax._

object Bittrex {
  implicit val summaryReads: Reads[models.bittrex.MarketSummary] = (
    (JsPath \ "MarketName").read[String] and
      (JsPath \ "High").read[BigDecimal] and
      (JsPath \ "Low").read[BigDecimal] and
      (JsPath \ "Volume").read[BigDecimal] and
      (JsPath \ "Last").read[BigDecimal] and
      (JsPath \ "BaseVolume").read[BigDecimal] and
      (JsPath \ "TimeStamp").read[String] and
      (JsPath \ "Bid").read[BigDecimal] and
      (JsPath \ "Ask").read[BigDecimal] and
      (JsPath \ "OpenBuyOrders").read[Int] and
      (JsPath \ "OpenSellOrders").read[Int] and
      (JsPath \ "PrevDay").read[BigDecimal] and
      (JsPath \ "Created").read[String]
    )(MarketSummary.apply _)

  implicit val marketsummaries = Json.reads[AllMarketSummary]
}
//"MarketName" : "BTC-888",
//"High" : 0.00000919,
//"Low" : 0.00000820,
//"Volume" : 74339.61396015,
//"Last" : 0.00000820,
//"BaseVolume" : 0.64966963,
//"TimeStamp" : "2014-07-09T07:19:30.15",
//"Bid" : 0.00000820,
//"Ask" : 0.00000831,
//"OpenBuyOrders" : 15,
//"OpenSellOrders" : 15,
//"PrevDay" : 0.00000821,
//"Created" : "2014-03-20T06:00:00",
//"DisplayMarketName" : null
case class AllMarketSummary(success: Boolean,
                            message: String,
                            result: List[MarketSummary])

case class MarketSummary(name: String,
                         high: BigDecimal,
                         low: BigDecimal,
                         volume: BigDecimal,
                         last: BigDecimal,
                         baseVolume: BigDecimal,
                         timeStamp: String,
                         bid: BigDecimal,
                         ask: BigDecimal,
                         openBuyOrders: Int,
                         openSellOrders: Int,
                         prevDay: BigDecimal,
                         created: String
                        )
