package database

// external
import akka.actor.{Actor, ActorSystem, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import org.scalatest._
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._

// internal
import models.strategies.GoldenCrossStrategy
import models.market.MarketStructures.MarketMessage
import models.strategies.TheoreticalPerfectStrategy


class PoloniexTradeSpec extends FlatSpec with PoloniexDatabase with BeforeAndAfter {
  // Implicit boilerplate necessary for creating akka-streams stuff
  implicit lazy val system = ActorSystem("poloniex-tests")
  implicit lazy val materializer = ActorMaterializer()

  override def afterAll() {
    Await.result(system.terminate, Duration.Inf)
  }

  case object Done

  "GoldenCrossStrategy" should "be positive" in {
    val processMessages: Future[BigDecimal] = {

      val promise = Promise[BigDecimal]()

      val actor = system.actorOf(Props(new Actor with GoldenCrossStrategy {
        setAllMarketAverages(exponentialMovingAverages(List(3, 17)))

        def receive = handleMessageUpdate orElse myReceive

        def myReceive: Receive = {
          case Done =>
            printResults()

            // complete our future with the final balance
            promise.success(totalBalance)
        }
      }))

      // Send Done when complete
      val sink = Sink.actorRef[MarketMessage](actor, onCompleteMessage = Done)

      messageSource
        .via(messageFlow)
        .to(sink)
        .run()

      promise.future
    }

    Await.result(processMessages, Duration.Inf)

    // must result in a profit
    assert(processMessages.futureValue > 1.0)
  }

//  // #Uncomment to compute the perfect scenario
//  "TheoreticalPerfectStrategy" should "be positive" in {
//     val process: Future[BigDecimal] = {
//
//       val promise = Promise[BigDecimal]()
//
//       val actor = system.actorOf(Props(new Actor with TheoreticalPerfectStrategy {
//         def receive = handleMessageUpdate orElse myReceive
//
//         def myReceive: Receive = {
//           case Done =>
//             println(balance)
//
//             // complete our future with the final balance
//             promise.success(balance)
//         }
//       }))
//
//       val sink = Sink.actorRef[MarketMessage2](actor, onCompleteMessage = Done)
//
//       messageSource
//         .via(messageFlow)
//         .to(sink)
//         .run()
//
//       promise.future
//     }
//
//     Await.result(process, Duration.Inf)
//     assert(process.futureValue > 1.0)
//   }
}