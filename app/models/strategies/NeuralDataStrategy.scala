package models.strategies

// internal
import models.analytics.individual.KitchenSink
import models.market.MarketCandle
import models.market.MarketStructures.{MarketMessage, Trade}
import models.neuron.NeuralNet

import scala.collection.mutable.{ArrayBuffer, ListBuffer}


/**
  * This strategy shall create the training data from historic candle data.
  *
  * @param context
  */
class NeuralDataStrategy(val context: KitchenSink) extends Strategy {


  val candleSampleNumber = 10

  def reset() = {
    context.buyList.clear()
    context.sellList.clear()
  }
  def train() = {}

  def createDataFromCandles(marketName: String) = {
    val candles = context.getCandles()
    val emas = context.getMovingAverages()

    val emasShorter = emas.head._2.reverse
    val emasLonger = emas.last._2.reverse

    var opportunites = 0

    // process 10 candles at a time
    val candlePatterns = ListBuffer[Array[Double]]()

    (0 until candles.length - candleSampleNumber).foreach { i =>
      val middle = candleSampleNumber/2
      val set = candles.slice(i, i + candleSampleNumber)

      val ema1Set = emasShorter.slice(i, i + candleSampleNumber/2)
      val ema2Set = emasLonger.slice(i, i + candleSampleNumber/2)
      val emaCombined = ema1Set.zip(ema2Set)

      val middleCandle = set(candleSampleNumber/2)

      // this is the overall percent gain in the second half of the set
      val percentGain = (set.last.close - middleCandle.open) / middleCandle.open
      val firstHalf = set.take(middle)
      val secondHalf = set.takeRight(middle)

      val numberOfBuyCandles = secondHalf.filter(_.isBuy()).length

      // if there are more buy candles than sell in the second half
      if (numberOfBuyCandles == candleSampleNumber/2 - 1 && percentGain > 0.01) {

        val openToClose = firstHalf.map(c => (c.close - c.open) / c.open)
        val lowToHigh = firstHalf.map(c => (c.high - c.low) / c.low)
        val emaDeltas = emaCombined.map(emas => (emas._1.ema - emas._2.ema) / emas._1.ema)

        val inputs = openToClose.map(_.toDouble).toArray.toBuffer
        inputs.appendAll(emaDeltas.map(_.toDouble))
        inputs.appendAll(lowToHigh.map(_.toDouble))

        //val rate1 = ((ema1Set.last.ema - ema1Set.head.ema) / 5) * 10000000
        //val rate2 = ((ema2Set.last.ema - ema2Set.head.ema) / 5) * 10000000
        //val rate1 = (ema1Set.last.ema - firstHalf.head.close) / firstHalf.head.close
        //val rate2 = (ema2Set.last.ema - firstHalf.head.close) / firstHalf.head.close


        if (opportunites == 1 || opportunites == 4 || opportunites == 8) {
          //context.buyList.append(Trade(marketName, set.head.time, set.head.open, 1))
          candlePatterns.append(inputs.toArray)
        }

        opportunites += 1
      }
    }

    val outputs = candlePatterns.length
    val neuralNet = new NeuralNet(Array(15, 7, outputs))
    // Train
    val targets = ListBuffer[Array[Double]]()
    (0 until 15000).foreach { i =>
      candlePatterns.view.zipWithIndex.foreach { case (inputs, index) =>
        neuralNet.feedForward(inputVals = inputs)
        val target = Array.fill(candlePatterns.length){0.0}
        target(index) = 1.0
        targets.append(target)
        neuralNet.backProp(target)
      }
    }

    println(context.buyList)

    // run ze test!
    (0 until candles.length - candleSampleNumber).foreach { i =>
      val middle = candleSampleNumber/2
      val set = candles.slice(i, i + candleSampleNumber)
      val middleCandle = set(candleSampleNumber/2)
      val ema1Set = emasShorter.slice(i, i + candleSampleNumber/2)
      val ema2Set = emasLonger.slice(i, i + candleSampleNumber/2)
      val emaCombined = ema1Set.zip(ema2Set)

      // this is the overall percent gain in the second half of the set
      val percentGain = (set.last.close - middleCandle.open) / middleCandle.open
      val firstHalf = set.take(middle)
      val secondHalf = set.takeRight(middle)

      val openToClose = firstHalf.map( c => (c.close - c.open) / c.open)
      val lowToHigh = firstHalf.map( c => (c.high - c.low) / c.low)
      val emaDeltas = emaCombined.map ( emas => (emas._1.ema - emas._2.ema) / emas._1.ema)

      val inputs = openToClose.map(_.toDouble).toArray.toBuffer
      inputs.appendAll(emaDeltas.map(_.toDouble))
      inputs.appendAll(lowToHigh.map(_.toDouble))

      //val rate1 = (ema1Set.last.ema - firstHalf.head.close) / firstHalf.head.close
      //val rate2 = (ema2Set.last.ema - firstHalf.head.close) / firstHalf.head.close
      //inputs.append(rate1.toDouble, rate2.toDouble)

      neuralNet.feedForward(inputs.toArray)
      val results = neuralNet.getResults()

      // if there are more buy candles than sell in the second half
      if (results.filter(_ > .90).length == 1 &&
      results.filter(_.abs < .01).length == outputs-1) {
        println(results.deep.mkString(" "))
        //context.sellList.append(Trade(marketName, set.head.time, set.head.open, 1))
        //println(percentGain)
      }
    }

    println(s"matches ${context.sellList.length}")
    println(context.sellList)


    //(0 to 2000).foreach { i =>
    //  data.foreach { s =>
    //    val inputs = s.take(s.length - 1).map(_.toDouble).toArray
    //    val output = Array(s.last.toDouble)
    //    neuralNet.feedForward(inputs)
    //    neuralNet.backProp  def tryBuy(msg: MarketMessage, ema1: BigDecimal, ema2: BigDecimal) = {
  }

  def printResults(): Unit = {
    println("Done")
  }

  def handleMessage(msg: MarketMessage) = {}
}
