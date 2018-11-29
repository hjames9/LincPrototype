package io.padium.audionlp.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.padium.audionlp.AudioException
import io.padium.audionlp.AudioProcessorLocation
import io.padium.audionlp.AudioTextResult
import io.padium.audionlp.AudioToTextListener
import java.util.concurrent.LinkedBlockingQueue

internal class GoogleAudioToTextImpl(context: Context, private val processorLocation: AudioProcessorLocation) : AudioToTextImpl {
    companion object {
        private val TAG = GoogleAudioToTextImpl::class.java.simpleName
    }

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    override val audioQueue = LinkedBlockingQueue<Pair<ShortArray, Int>>()

    init {
        if(!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw AudioException("Google speech recognition is NOT available")
        }
        Log.i(TAG, "Initialized Google speech recognition")
    }

    override fun process(listener: AudioToTextListener, parameters: AudioParameters?) {
        val recognitionListener = object : RecognitionListener {
            override fun onBeginningOfSpeech() {
                listener.onStart(processorLocation)
            }

            override fun onReadyForSpeech(params: Bundle?) {
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.i(TAG, "Unknown event occurred $eventType")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "Buffer received of size ${buffer?.size}")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if(null != partialResults) {
                    val resultsList = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    resultsList.forEach { item ->
                        val result = AudioTextResult(item, 0.0, 0.0)
                        listener.onPartialResult(processorLocation, result)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if(null != results) {
                    val resultsList = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                    resultsList.forEachIndexed { index, item ->
                        val result = AudioTextResult(item, 0.0, confidences[index].toDouble())
                        listener.onResult(processorLocation, result)
                    }
                }
            }

            override fun onEndOfSpeech() {
                listener.onEnd(processorLocation)
            }

            override fun onError(error: Int) {
                listener.onError(processorLocation, AudioException("Error occurred: $error"))
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.i(TAG, "RMS DB value $rmsdB")
            }
        }
        recognizer.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, processorLocation == AudioProcessorLocation.LOCAL)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        recognizer.startListening(intent)
    }

    override fun close() {
        recognizer.stopListening()
        recognizer.cancel()
    }
}