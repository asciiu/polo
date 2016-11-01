package models.neuron

import scala.collection.mutable.ArrayBuffer

/**
  * Defines a Neural Net.
  * @param typology number of neurons in each layer defined as an array e.g. Array(3, 2, 1)
  */
class NeuralNet(val typology: Array[Int]) {

  // must have at least 2 layers
  require(typology.length > 1)
  // TODO cannot have 0 neuron layer

  val numLayers = typology.length
  var error: Double = 0.0
  var recentAverageError: Double = 0.0
  var recentAverageSmoothingFactor: Double = 0.0


  val layers: Array[Layer] = (for (layerNum <- 0 until numLayers)
    yield {
      val outputs = if (layerNum == numLayers -1) 0 else typology(layerNum+1)
      new Layer(typology(layerNum), outputs)
    }).toArray


  /**
    * Feeds the inputs to the network
    * @param inputVals must have same number of inputs as first layer neurons
    * @return
    */
  def feedForward(inputVals: Array[Double]) = {
    // must have enough inputs for each neuron in layer 1
    require(inputVals.length == typology(0))

    // assign the input values to the input neurons
    for (i <- 0 until inputVals.length) {
      // input neurons are in layer 1
      layers(0).neurons(i).setOutputValue(inputVals(i))
    }

    // forward propagate starting with first hidden layer
    for (layerNum <- 1 until layers.length) {

      val previousLayer = layers(layerNum-1)

      // each neuron except bias neuron
      for (n <- 0 until layers(layerNum).neurons.length - 1) {
        layers(layerNum).neurons(n).feedForward(previousLayer)
      }
    }
  }

  def backProp(targetVals: Array[Double]) = {

    // STEP 1: calc overall net error as root mean square
    val outputLayer = layers.last

    // must have enough vals for each output neuron minus the bias neuron
    require (targetVals.length == outputLayer.neurons.length - 1)

    error = 0.0

    // remove the bias neuron
    for (n <- 0 until outputLayer.neurons.length - 1) {
      val delta = targetVals(n) - outputLayer.neurons(n).outputValue
      error = delta * delta
    }

    error /= (outputLayer.neurons.length - 1)
    error = Math.sqrt(error)

    // implements a recent average measure
    recentAverageError = (recentAverageError * recentAverageSmoothingFactor + error) / (recentAverageSmoothingFactor + 1)

    // STEP 2: calc output layer gradients
    for (n <- 0 until outputLayer.neurons.length - 1) {
      outputLayer.neurons(n).calcOutputGradients(targetVals(n))
    }

    // STEP 3: calc hidden layer gradients
    for (layerNum <- layers.length-2 until 0 by -1) {
      val hiddenLayer = layers(layerNum)
      val nextLayer = layers(layerNum+1)

      for (n <- 0 until hiddenLayer.neurons.length) {
        hiddenLayer.neurons(n).calcHiddenGradients(nextLayer)
      }
    }

    // STEP 4: for all layers from outputs to first hidden layer
    // update connection weights
    for (layerNum <- layers.length - 1 until 0 by -1) {
      val layer = layers(layerNum)
      val previousLayer = layers(layerNum-1)

      // update weights for each neuron in layer
      for (n <- 0 until layer.neurons.length - 1) {
        layer.neurons(n).updateInputWeights(previousLayer)
      }
    }
  }

  def getResults(): Array[Double] = {

    val results = ArrayBuffer[Double]()
    val lastLayer = layers.last

    // we don't care about the last bias neuron in the last layer
    for (n <- 0 until lastLayer.neurons.length - 1) {
      results.append(lastLayer.neurons(n).outputValue)
    }
    results.toArray
  }
}

