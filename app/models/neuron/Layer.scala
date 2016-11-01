package models.neuron

/**
  * Defines a neuron layer.
  * @param numNeurons number of nuerons in this layer
  * @param outputsPerNeuron defines the number of outputs per neuron
  */
class Layer(val numNeurons: Int, val outputsPerNeuron: Int) {
  // adds a bias neuron
  val neurons = (0 to numNeurons).map { index =>
    new Neuron(outputsPerNeuron, index)
  }.toArray

  // force bias node's output to 1.0 it will the last neuron created
  neurons.last.setOutputValue(1.0)
}
