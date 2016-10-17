package database

// external
import java.time.OffsetDateTime

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import models.db.Tables.{PoloniexCandleRow, _}
import models.market.{ClosePrice, ExponentialMovingAverages, MarketExponentialMovingAvgs}
import models.poloniex.{MarketCandle, MarketMessage2}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import services.actors.GoldenCross

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import slick.backend.DatabasePublisher

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.math.BigDecimal.RoundingMode

// internal
import models.db.Tables
import models.db.Tables.profile.api._
import models.poloniex.{MarketMessage, MarketUpdate}
import utils.Misc


class PoloniexMessageSpec extends FlatSpec with ScalaFutures with PostgresSpec with BeforeAndAfter {
  // Implicit boilerplate necessary for creating akka-streams stuff
  implicit lazy val system = ActorSystem("reactive-streams-end-to-end")
  implicit lazy val materializer = ActorMaterializer()


  def exponentialMovingAverages(): ExponentialMovingAverages = {
    val query = PoloniexCandle.filter(_.cryptoCurrency.startsWith("BTC_"))

    val result: Future[Seq[PoloniexCandleRow]] = database.run(query.result)

    val averages = result.map { t =>

      val groupByMarket = t.groupBy(_.cryptoCurrency)
      val movingAverages = new ExponentialMovingAverages()

      for (marketName <- groupByMarket.keys) {
        // sort in descending time
        val sortedRows = groupByMarket(marketName).sortBy(_.createdAt).reverse

        val closePrices = sortedRows.map { c => ClosePrice(c.createdAt, c.close) }.toList
        movingAverages.setInitialMarketClosePrices(marketName, closePrices)
      }

      movingAverages
    }

    Await.result(averages, Duration.Inf)
    averages.futureValue
  }

  def exponentialMovingAveragesImproved(periods: List[Int] = List(7, 15)): Map[String, List[MarketExponentialMovingAvgs]] = {
    val minutes = 5
    val query = PoloniexCandle.filter(_.cryptoCurrency.startsWith("BTC_"))

    val result: Future[Seq[PoloniexCandleRow]] = database.run(query.result)
    val map = scala.collection.mutable.Map[String, List[MarketExponentialMovingAvgs]]()

    val averages = result.map { t =>

      val groupByMarket = t.groupBy(_.cryptoCurrency)
      val movingAverages = new ExponentialMovingAverages()

      for (marketName <- groupByMarket.keys) {
        // sort in descending time
        val sortedRows = groupByMarket(marketName).sortBy(_.createdAt).reverse

        val closePrices = sortedRows.map { c => ClosePrice(c.createdAt, c.close) }.toList

        val avgs = for (period <- periods) yield new MarketExponentialMovingAvgs(marketName, period, minutes, closePrices)
        map.put(marketName, avgs)
      }

      map.toMap
    }

    Await.result(averages, Duration.Inf)
    averages.futureValue
  }

  // #1 The cross strategy with the original ema classes
//  "Gold cross" should "be positive" in {
//    val query = Tables.PoloniexMessage.filter(_.cryptoCurrency.startsWith("BTC_")).sortBy(_.createdAt).result
//    val publisher: DatabasePublisher[Tables.PoloniexMessageRow] = database.stream(
//      query.transactionally.withStatementParameters(fetchSize = 1000)
//    )
//
//    val poloniexMessageSource: Source[Tables.PoloniexMessageRow, NotUsed] = Source.fromPublisher(publisher)
//
//    val convert: Flow[Tables.PoloniexMessageRow, MarketMessage2, NotUsed] =
//      Flow[Tables.PoloniexMessageRow].map { row =>
//        MarketMessage2(
//          row.createdAt,
//          row.cryptoCurrency,
//          row.last,
//          row.lowestAsk,
//          row.highestBid,
//          row.percentChange,
//          row.baseVolume,
//          row.quoteVolume,
//          row.isFrozen.toString,
//          row.high24hr,
//          row.low24hr)
//      }
//
//    val averages = exponentialMovingAverages()
//    //val markets = scala.collection.mutable.Map[String, BigDecimal]()
//    val marketWatch = scala.collection.mutable.Set[String]()
//    val buyRecords = scala.collection.mutable.Map[String, BigDecimal]()
//    var totalPercentage: BigDecimal = 0.0
//    var trades = 0
//
//    val process: Future[Unit] = {
//
//      val p = Promise[Unit]()
//
//      val actor = system.actorOf(Props(new Actor {
//        override def receive = {
//          case msg: MarketMessage2 =>
//
//            val marketName = msg.cryptoCurrency
//            val currentPrice = msg.last
//
//            val normalizedTime = Misc.roundDateToMinute(msg.time, 5)
//            val emas = averages.update(marketName, ClosePrice(normalizedTime, currentPrice))
//
//            val ema1 = emas.head
//            val ema2 = emas.last
//
////            if (ema1._2.ema < ema2._2.ema) {
////              marketWatch += marketName
////
////            } else if (ema1._2.ema > ema2._2.ema && marketWatch.contains(marketName)){
////
////              val ema1Curr = ema1._2.ema
////              val ema1Prev = averages.getMovingAverages(marketName, ema1._1)(1).ema
////              val delta = ema1Curr - ema1Prev / 5
////
////              if (delta > 0.01 && !buyRecords.contains(marketName)) {
////
////                println(s"Buy $marketName at ${msg.time} price: $currentPrice")
////
////                buyRecords(marketName) = currentPrice
////                marketWatch -= marketName
////              }
////
////              marketWatch -= marketName
////            } else if ( ema1._2.ema > ema2._2.ema) {
////
////              if (buyRecords.contains(marketName)) {
////                // sell when the ema1 for this period is less than the previous ema1
////                val ema1Curr = ema1._2.ema
////                val ema1Prev = averages.getMovingAverages(marketName, ema1._1)(1).ema
////
////                if (ema1Prev > ema1Curr) {
////
////                  println(s"Sell $marketName at ${msg.time} price: $currentPrice")
////
////                  val buyPrice = buyRecords(marketName)
////                  val percent = (currentPrice - buyPrice) / buyPrice
////                  totalPercentage += percent
////                  trades += 1
////                  buyRecords.remove(marketName)
////                }
////              }
////            }
//
//            if (ema1._2.ema < ema2._2.ema) {
//              if (!buyRecords.contains(marketName)) {
//                buyRecords(marketName) = currentPrice
//                //println(s"Buy $marketName at ${msg.time} for $currentPrice")
//              }
//            } else if (ema1._2.ema > ema2._2.ema){
//
//              if (buyRecords.contains(marketName)) {
//                val buyPrice = buyRecords(marketName)
//                val diff = currentPrice - buyPrice
//                val percent = diff / buyPrice
//
//                println(s"Sell $marketName at ${msg.time} for $currentPrice bought at $buyPrice diff ${diff.setScale(8, RoundingMode.FLOOR)}")
//
//                trades += 1
//                totalPercentage += percent
//                buyRecords.remove(marketName)
//              }
//            }
//
//          case "done" => p.success(())
//        }
//      }))
//
//      val sink = Sink.actorRef[MarketMessage2](actor, onCompleteMessage = "done")
//
//      poloniexMessageSource
//        .via(convert)
//        .to(sink)
//        .run()
//
//      p.future
//    }
//
//    Await.result(process, Duration.Inf)
//    Await.result(system.terminate, Duration.Inf)
//    println((totalPercentage).setScale(2, BigDecimal.RoundingMode.FLOOR))
//    println(trades)
//
//    assert(true)
//  }

  // #2 The gold cross with new market classes.
  "The Golden Cross trade stategy using the new classes" should "be positive" in {
    val query = Tables.PoloniexMessage.filter(_.cryptoCurrency.startsWith("BTC_")).sortBy(_.createdAt).result
    val publisher: DatabasePublisher[Tables.PoloniexMessageRow] = database.stream(
      query.transactionally.withStatementParameters(fetchSize = 1000)
    )

    val poloniexMessageSource: Source[Tables.PoloniexMessageRow, NotUsed] = Source.fromPublisher(publisher)

    val convert: Flow[Tables.PoloniexMessageRow, MarketMessage2, NotUsed] =
      Flow[Tables.PoloniexMessageRow].map { row =>
        MarketMessage2(
          row.createdAt,
          row.cryptoCurrency,
          row.last,
          row.lowestAsk,
          row.highestBid,
          row.percentChange,
          row.baseVolume,
          row.quoteVolume,
          row.isFrozen.toString,
          row.high24hr,
          row.low24hr)
      }

    val processMessages: Future[Unit] = {

      val promise = Promise[Unit]()

      val actor = system.actorOf(Props(new Actor with GoldenCross {
        setAllMarketAverages(exponentialMovingAveragesImproved(List(3, 17)))

        def receive = handleMessageUpdate orElse done

        def done: Receive = {
          case "Done" =>
            printResults()

            // complete the future
            promise.success(())
        }
      }))

      // Send Done when complete
      val sink = Sink.actorRef[MarketMessage2](actor, onCompleteMessage = "Done")

      poloniexMessageSource
        .via(convert)
        .to(sink)
        .run()

      promise.future
    }

    Await.result(processMessages, Duration.Inf)
    Await.result(system.terminate, Duration.Inf)

    assert(true)
  }

  // #3 The perfect trade scenario.
//  "the database" should "work" in {
//     val query = Tables.PoloniexMessage.filter(_.cryptoCurrency.startsWith("BTC_")).sortBy(_.createdAt).result
//     val publisher: DatabasePublisher[Tables.PoloniexMessageRow] = database.stream(
//       query.transactionally.withStatementParameters(fetchSize = 1000)
//     )
//
//     val poloniexMessageSource: Source[Tables.PoloniexMessageRow, NotUsed] = Source.fromPublisher(publisher)
//
//     val convert: Flow[Tables.PoloniexMessageRow, MarketUpdate, NotUsed] =
//       Flow[Tables.PoloniexMessageRow].map { row =>
//         val msg = MarketMessage(0, row.last, row.lowestAsk, row.highestBid, row.percentChange, row.baseVolume,
//           row.quoteVolume, row.isFrozen.toString, row.high24hr, row.low24hr)
//         MarketUpdate(row.cryptoCurrency, msg)
//       }
//
//     var balance: BigDecimal = 1.0
//     val markets = scala.collection.mutable.Map[String, BigDecimal]()
//     val buyRecords = scala.collection.mutable.Map[String, BigDecimal]()
//
//     val process: Future[Unit] = {
//
//       val p = Promise[Unit]()
//
//       val actor = system.actorOf(Props(new Actor {
//         override def receive = {
//           case update: MarketUpdate =>
//
//             val marketName = update.name
//             val currentPrice = update.info.last
//
//             if (markets.contains(marketName)) {
//               val previousPrice = markets(update.name)
//
//               // if the update price is greater than the previous price
//               // update the stored price
//               if (previousPrice < currentPrice) {
//
//                 // store a record for the lowest price
//                 if (!buyRecords.contains(marketName) && balance > previousPrice) {
//                   buyRecords(marketName) = previousPrice
//                   balance -= previousPrice
//                 }
//
//               } else if (previousPrice > currentPrice) {
//
//                 if (buyRecords.contains(marketName)) {
//                   val buyPrice = buyRecords(marketName)
//                   val percent = (previousPrice - buyPrice) / buyPrice
//                   buyRecords.remove(marketName)
//                   balance += previousPrice
//                 }
//               }
//             }
//             markets(marketName) = currentPrice
//
//           case "done" => p.success(())
//         }
//       }))
//
//       val sink = Sink.actorRef[MarketUpdate](actor, onCompleteMessage = "done")
//
//       poloniexMessageSource
//         .via(convert)
//         .to(sink)
//         .run()
//
//       p.future
//     }
//
//     Await.result(process, Duration.Inf)
//     Await.result(system.terminate, Duration.Inf)
//
//     println((balance).setScale(2, BigDecimal.RoundingMode.FLOOR))
//
//     assert(true)
//   }
}