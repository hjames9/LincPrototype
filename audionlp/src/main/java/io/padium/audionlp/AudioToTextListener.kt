package io.padium.audionlp

interface AudioToTextListener {
    fun onStart()
    fun onResult(result: String)
    fun onPartialResult(result: String)
    fun onEnd()
    fun onError(exp: Exception)
}