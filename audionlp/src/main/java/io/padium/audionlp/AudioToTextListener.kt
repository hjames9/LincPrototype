package io.padium.audionlp

interface AudioToTextListener {
    fun onStart(processorLocation: AudioProcessorLocation)
    fun onResult(processorLocation: AudioProcessorLocation, result: AudioTextResult)
    fun onPartialResult(processorLocation: AudioProcessorLocation, result: AudioTextResult)
    fun onEnd(processorLocation: AudioProcessorLocation)
    fun onError(processorLocation: AudioProcessorLocation, exp: Exception)
}