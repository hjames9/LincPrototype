package io.padium.audionlp.impl

import android.content.Context
import android.util.Log
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Config
import edu.cmu.pocketsphinx.Decoder
import io.padium.audionlp.AudioProcessorLocation
import io.padium.audionlp.AudioTextResult
import io.padium.audionlp.AudioToTextListener
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class PocketSphinxAudioToTextImpl(context: Context, private val recording: AtomicBoolean) : AudioToTextImpl {
    companion object {
        private val TAG = PocketSphinxAudioToTextImpl::class.java.simpleName
        private const val SPHINX_ACOUSTIC_MODEL_PATH = "en-us-ptm"
        private const val SPHINX_DICTIONARY_PATH = "cmudict-en-us.dict"
        private const val KWS_SEARCH = "wakeup"
        private const val FORECAST_SEARCH = "forecast"
        private const val DIGITS_SEARCH = "digits"
        private const val PHONE_SEARCH = "phones"
        private const val ALLWORDS_SEARCH = "allwords"
        private const val COOKING_SEARCH = "cooking"
        private const val KEYPHRASE = "oh mighty computer"
    }

    private val decoder : Decoder
    private val config : Config
    override val audioQueue = LinkedBlockingQueue<Pair<ShortArray, Int>>()

    init {
        val assets = Assets(context)
        assets.syncAssets()

        System.loadLibrary("pocketsphinx_jni")
        config = Decoder.defaultConfig()
        config.setString("-hmm", File(assets.externalDir, SPHINX_ACOUSTIC_MODEL_PATH).path)
        config.setString("-dict", File(assets.externalDir, SPHINX_DICTIONARY_PATH).path)
        //config.setFloat("-kws_threshold", 0.000000000000000000000000000000000000001) //0.00001 - .00000000000000000001
        decoder = Decoder(config)
        decoder.setKeyphrase(KWS_SEARCH, KEYPHRASE)
        decoder.setJsgfFile(DIGITS_SEARCH, File(assets.externalDir, "digits.gram").path)
        decoder.setJsgfFile(ALLWORDS_SEARCH, File(assets.externalDir, "allwords.gram").path)
        decoder.setJsgfFile(COOKING_SEARCH, File(assets.externalDir, "cooking.gram").path)
        decoder.setLmFile(FORECAST_SEARCH, File(assets.externalDir, "weather.dmp").path)
        decoder.setAllphoneFile(PHONE_SEARCH, File(assets.externalDir, "en-phone.dmp").path)
        decoder.search = COOKING_SEARCH

        Log.d(TAG, "Decoder sample rate is ${decoder.config.getFloat("-samprate").toInt()}")
        Log.i(TAG, "Initialized PocketSphinx speech recognition")
    }

    override fun process(listener: AudioToTextListener, parameters: AudioParameters) {
        try {
            decoder.startUtt()
            listener.onStart(AudioProcessorLocation.LOCAL)

            while (audioQueue.isNotEmpty() || recording.get()) {
                val bufferPair = audioQueue.poll(500, TimeUnit.MILLISECONDS)
                if(null != bufferPair) {
                    decoder.processRaw(bufferPair.first, bufferPair.second.toLong(), false, false)

                    val hypothesis = decoder.hyp()
                    if (null != hypothesis) {
                        val result = AudioTextResult(hypothesis.hypstr, hypothesis.bestScore, hypothesis.prob)
                        listener.onPartialResult(AudioProcessorLocation.LOCAL, result)
                    }
                }
            }
            decoder.endUtt()

            val hypothesis = decoder.hyp()
            if(null != hypothesis) {
                val result = AudioTextResult(hypothesis.hypstr, hypothesis.bestScore, hypothesis.prob)
                listener.onResult(AudioProcessorLocation.LOCAL, result)
            }
            listener.onEnd(AudioProcessorLocation.LOCAL)
        } catch(e: InterruptedException) {
        }
    }

    override fun close() {
    }
}