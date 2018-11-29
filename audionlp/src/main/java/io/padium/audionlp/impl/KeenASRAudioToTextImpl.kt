package io.padium.audionlp.impl

import android.content.Context
import io.padium.audionlp.AudioToTextListener
import java.util.concurrent.LinkedBlockingQueue
import android.util.Log
import com.keenresearch.keenasr.KASRBundle
import java.io.IOException
import com.keenresearch.keenasr.KASRRecognizer
import com.keenresearch.keenasr.KASRRecognizerListener
import com.keenresearch.keenasr.KASRResult
import com.keenresearch.keenasr.KASRDecodingGraph
import io.padium.audionlp.AudioException
import io.padium.audionlp.AudioProcessorLocation
import io.padium.audionlp.AudioTextResult

internal class KeenASRAudioToTextImpl(context: Context) : AudioToTextImpl {
    companion object {
        private val TAG = KeenASRAudioToTextImpl::class.java.simpleName
    }

    override val audioQueue = LinkedBlockingQueue<Pair<ShortArray, Int>>()
    private val recognizer = KASRRecognizer.sharedInstance()

    init {
        val asrBundle = KASRBundle(context)
        val assets = ArrayList<String>()

        // you will need to make sure all individual assets are added to the ArrayList
        assets.add("keenB2mQT-nnet3chain-en-us/decode.conf")
        assets.add("keenB2mQT-nnet3chain-en-us/final.dubm")
        assets.add("keenB2mQT-nnet3chain-en-us/final.ie")
        assets.add("keenB2mQT-nnet3chain-en-us/final.mat")
        assets.add("keenB2mQT-nnet3chain-en-us/final.mdl")
        assets.add("keenB2mQT-nnet3chain-en-us/global_cmvn.stats")
        assets.add("keenB2mQT-nnet3chain-en-us/ivector_extractor.conf")
        assets.add("keenB2mQT-nnet3chain-en-us/mfcc.conf")
        assets.add("keenB2mQT-nnet3chain-en-us/online_cmvn.conf")
        assets.add("keenB2mQT-nnet3chain-en-us/splice.conf")
        assets.add("keenB2mQT-nnet3chain-en-us/splice_opts")
        assets.add("keenB2mQT-nnet3chain-en-us/wordBoundaries.int")
        assets.add("keenB2mQT-nnet3chain-en-us/words.txt")

        assets.add("keenB2mQT-nnet3chain-en-us/lang/lexicon.txt")
        assets.add("keenB2mQT-nnet3chain-en-us/lang/phones.txt")
        assets.add("keenB2mQT-nnet3chain-en-us/lang/tree")

        val asrBundleRootPath = context.applicationInfo.dataDir
        val asrBundlePath = "$asrBundleRootPath/keenB2mQT-nnet3chain-en-us"
        try {
            asrBundle.installASRBundle(assets, asrBundleRootPath)
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when installing ASR bundle$e")
        }

        KASRRecognizer.initWithASRBundleAtPath(asrBundlePath, context)

        Log.i(TAG, "Initialized Keen ASR speech recognition")
    }

    override fun process(listener: AudioToTextListener, parameters: AudioParameters?) {
        val keenListener = object : KASRRecognizerListener {
            override fun onPartialResult(recognizer: KASRRecognizer?, keenResult: KASRResult?) {
                if(null != keenResult) {
                    Log.i(TAG, "   Partial result: $keenResult.text")

                    val result = AudioTextResult(keenResult.text, 0.0, keenResult.confidence.toDouble())
                    listener.onPartialResult(AudioProcessorLocation.LOCAL, result)
                }
            }

            override fun onFinalResult(recognizer: KASRRecognizer?, keenResult: KASRResult?) {
                if(null != keenResult) {
                    Log.i(TAG, "Final result: $keenResult")

                    val result = AudioTextResult(keenResult.text, 0.0, keenResult.confidence.toDouble())
                    listener.onResult(AudioProcessorLocation.LOCAL, result)
                    listener.onEnd(AudioProcessorLocation.LOCAL)
                }
            }
        }

        if (null != recognizer) {
            recognizer.addListener(keenListener)
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForGoodMatch, 1.0f)
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutEndSilenceForAnyMatch, 1.0f)
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutMaxDuration, 10.0f)
            recognizer.setVADParameter(KASRRecognizer.KASRVadParameter.KASRVadTimeoutForNoSpeech, 3.0f)
            recognizer.createAudioRecordings = false

            // getPhrases is a method that returns an array of phrases
            val phrases = arrayOf("")
            val dgName = "words"
            KASRDecodingGraph.createDecodingGraphFromSentences(phrases, recognizer, dgName)
            recognizer.prepareForListeningWithCustomDecodingGraphWithName(dgName)
        } else {
            listener.onError(AudioProcessorLocation.LOCAL, AudioException("Unable to retrieve recognizer"))
        }

        listener.onStart(AudioProcessorLocation.LOCAL)
        recognizer.startListening()
    }

    override fun close() {
        recognizer.stopListening()
    }
}