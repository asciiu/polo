package models.neuron

/**
  * Defines the output weights from a neuron to
  * its outputs.
  * @param weight
  * @param deltaWeight
  */
case class Connection(var weight: Double, var deltaWeight: Double)

/**
  * Defines a neuron with a number of outputs.
  * @param numOutputs
  * @param myIndex
  */
class Neuron(val numOutputs: Int, val myIndex: Int) {

  private var gradient: Double = 0
  private var outputVal: Double = 0.0
  private val outputWeights = Array.fill(numOutputs)(Connection(randomWeight,0))

  // [0.0..1.0] overall net training rate
  val eta: Double = 0.15
  // [0.0..n] multiplier of last weight change (momentum)
  val alpha: Double = 0.5

  private def randomWeight = Math.random()

  /**
    * Also known as the activation function.
    * @param x
    * @return
    */
  private def transferFunction(x: Double): Double = {
    // tanh - output range [-1.0..1.0]
    Math.tanh(x)
  }

  private def transferFunctionDerivative(x: Double): Double = {
    // this is a quick approx of the derivative of tanh
    1.0 - (x*x)
  }

  private def sumDOW(nextLayer: Layer): Double = {
    var sum = 0.0
    // sum our contributions of the nodes we feed
    for (n <- 0 until nextLayer.neurons.length - 1) {
      sum += outputWeights(n).weight * nextLayer.neurons(n).gradient
    }
    sum
  }

  override def toString() = s"I am a neuron!"

  def setOutputValue(output: Double) = outputVal = output
  def outputValue = outputVal

  def feedForward(previousLayer: Layer) = {
    // all output * weight from previous layer
    var sum = previousLayer.neurons.map( n =>  n.outputVal * n.outputWeights(myIndex).weight ).sum

    outputVal = transferFunction(sum)
  }

  def calcOutputGradients(targetVal: Double) = {
    val delta = targetVal - outputVal
    gradient = delta * transferFunctionDerivative(outputVal)
  }

  def calcHiddenGradients(nextLayer: Layer) = {
    val dow = sumDOW(nextLayer)
    gradient = dow * transferFunctionDerivative(outputVal)
  }

  def updateInputWeights(previousLayer: Layer) = {
    // the weights to be updated are in the Connection case class
    // in the neurons of the previous layer
    for (n <- 0 until previousLayer.numNeurons) {
      val neuron = previousLayer.neurons(n)
      val oldDeltaWeight = neuron.outputWeights(myIndex).deltaWeight

      // eta - overall net learning rate
      // 0.0 - slow learner
      // 0.2 - medium learner
      // 1.0 - reckless learner

      // alpha - momentum
      // 0.0 - no momentum
      // 0.5 - moderate momentum
      val newDeltaWeight =
      // individual input, magnified by the gradient and train rate
        eta * neuron.outputVal * gradient + alpha * oldDeltaWeight

      neuron.outputWeights(myIndex).deltaWeight = newDeltaWeight
      neuron.outputWeights(myIndex).weight += newDeltaWeight

    }
  }
}

