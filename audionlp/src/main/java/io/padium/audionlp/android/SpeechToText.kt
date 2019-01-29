package io.padium.audionlp.android

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class SpeechToText(private var context: Context, private val delegate: SpeechDelegate) {
    companion object {
        private val TAG = SpeechToText::class.java.simpleName
    }

    private var mSpeechRecognizer: SpeechRecognizer? = null
    private var handler = Handler(Looper.getMainLooper())
    private var mPreferOffline = false
    private var mGetPartialResults = true
    private var mIsListening = false

    private val mPartialData = ArrayList<String>()
    private var mUnstableData: String? = null

    private var mDelayedStopListening: DelayedOperation? = null

    private var mLocale = Locale.getDefault()
    private var mStopListeningDelayInMs: Long = 10000
    private var mTransitionMinimumDelay: Long = 1200
    private var mLastActionTimestamp: Long = 0
    private var mLastPartialResults: List<String>? = null

    private val mListener = object : RecognitionListener {
        override fun onReadyForSpeech(bundle: Bundle) {
            mPartialData.clear()
            mUnstableData = null
        }

        override fun onBeginningOfSpeech() {
            mDelayedStopListening!!.start(object : DelayedOperation.Operation {
                override fun onDelayedOperation() {
                    returnPartialResultsAndRecreateSpeechRecognizer()
                    Log.d(TAG, "Stopping")
                }

                override fun shouldExecuteDelayedOperation(): Boolean {
                    return true
                }
            })
        }

        override fun onRmsChanged(v: Float) {
            try {
                delegate.onSpeechRmsChanged(v)
            } catch (exc: Throwable) {
                Log.e(TAG, "Unhandled exception in delegate onSpeechRmsChanged", exc)
            }
        }

        override fun onPartialResults(bundle: Bundle) {
            mDelayedStopListening!!.resetTimer()

            val partialResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val unstableData = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")

            if (partialResults != null && !partialResults.isEmpty()) {
                mPartialData.clear()
                mPartialData.addAll(partialResults)
                mUnstableData = if (unstableData != null && !unstableData.isEmpty())
                    unstableData[0]
                else
                    null
                try {
                    if (mLastPartialResults == null || mLastPartialResults != partialResults) {
                        delegate.onSpeechPartialResults(partialResults)
                        mLastPartialResults = partialResults
                    }
                } catch (exc: Throwable) {
                    Log.e(TAG, "Unhandled exception in delegate onSpeechPartialResults", exc)
                }

            }
        }

        override fun onResults(bundle: Bundle) {
            mDelayedStopListening!!.cancel()

            val results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val result = when(results != null && !results.isEmpty() && results[0] != null && !results[0].isEmpty()) {
                true -> results[0]
                false -> {
                    Log.i(TAG, "No speech results, getting partial")
                    getPartialResultsAsString()
                }
            }

            mIsListening = false

            try {
                delegate.onSpeechResult(result.trim { it <= ' ' })
            } catch (exc: Throwable) {
                Log.e(TAG, "Unhandled exception in delegate onSpeechResult", exc)
            }

            initSpeechRecognizer(context)
        }

        override fun onError(code: Int) {
            Log.e(TAG, "Speech recognition error", SpeechRecognitionException(code))
            returnPartialResultsAndRecreateSpeechRecognizer()
        }

        override fun onBufferReceived(bytes: ByteArray) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(i: Int, bundle: Bundle) {}
    }

    init {
        initSpeechRecognizer(context)
    }

    private fun initSpeechRecognizer(context: Context) {
        val queue = LinkedBlockingQueue<Any>()
        val processed = handler.post {
            try {
                this.context = context
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    if (mSpeechRecognizer != null) {
                        try {
                            mSpeechRecognizer!!.destroy()
                        } catch (exc: Throwable) {
                            Log.d(TAG, "Non-Fatal e while destroying speech. " + exc.message)
                        } finally {
                            mSpeechRecognizer = null
                        }
                    }

                    mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    mSpeechRecognizer!!.setRecognitionListener(mListener)
                    initDelayedStopListening(context)

                } else {
                    mSpeechRecognizer = null
                }

                mPartialData.clear()
                mUnstableData = null
                queue.add(Any())
            } catch(exc: Exception) {
                queue.add(exc)
            }
        }

        if(isOnMainThread())
            return

        if(processed) {
            Log.d(TAG, "Handler message was added to the queue")
            val value = queue.take()
            when (value) {
                value is Exception -> throw value as Exception
                else -> Log.d(TAG, "${object {}.javaClass.enclosingMethod.name} executed normally")
            }
        } else {
            Log.e(TAG, "Handler message was NOT added to the queue")
        }
    }

    private fun initDelayedStopListening(context: Context) {
        if (mDelayedStopListening != null) {
            mDelayedStopListening!!.cancel()
            mDelayedStopListening = null
        }

        try {
            startListening()
        } catch(exc: SpeechRecognitionException) {
            Log.e(TAG, exc.message, exc)
        }

        mDelayedStopListening = DelayedOperation(context, "delayStopListening", mStopListeningDelayInMs)
    }

    /**
     * Must be called inside Activity's onDestroy.
     */
    @Synchronized
    fun shutdown() {
        val queue = LinkedBlockingQueue<Any>()
        val processed = handler.post {
            try {
                if (mSpeechRecognizer != null) {
                    try {
                        mSpeechRecognizer!!.stopListening()
                    } catch (exc: Exception) {
                        Log.e(TAG, "Warning while de-initing speech recognizer", exc)
                    }
                }
                queue.add(Any())
            } catch(exc: Exception) {
                queue.add(exc)
            }
        }

        if(isOnMainThread())
            return

        if(processed) {
            Log.d(TAG, "Handler message was added to the queue")
            val value = queue.take()
            when (value) {
                value is Exception -> throw value as Exception
                else -> Log.d(TAG, "${object {}.javaClass.enclosingMethod.name} executed normally")
            }
        } else {
            Log.e(TAG, "Handler message was NOT added to the queue")
        }
    }

    /**
     * Starts voice recognition.
     *
     * @throws SpeechRecognitionException when speech recognition is not available on the device
     * @throws SpeechRecognitionException when google voice typing is disabled on the device
     */
    @Throws(SpeechRecognitionException::class)
    fun startListening() {
        if (isListening()) {
            muteBeepSoundOfRecorder(true)
            stopListening()
            return
        }

        val queue = LinkedBlockingQueue<Any>()
        val processed = handler.post {
            try {
                if (mIsListening) return@post

                if (mSpeechRecognizer == null)
                    throw SpeechRecognitionException("Speech recognition not available")

                if (throttleAction()) {
                    Log.d(TAG, "Hey man calm down! Throttling start to prevent disaster!")
                    return@post
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, mGetPartialResults)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, mLocale.language)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, mPreferOffline)

                try {
                    mSpeechRecognizer!!.startListening(intent)
                } catch (exc: SecurityException) {
                    throw SpeechRecognitionException("Google voice typing must be enabled")
                }

                mIsListening = true
                updateLastActionTimestamp()

                try {
                    delegate.onStartOfSpeech()
                } catch (exc: Throwable) {
                    Log.e(TAG, "Unhandled exception in delegate onStartOfSpeech", exc)
                }

                queue.add(Any())
            } catch(exc: Exception) {
                queue.add(exc)
            } finally {
                muteBeepSoundOfRecorder(true)
            }
        }

        if(isOnMainThread())
            return

        if(processed) {
            Log.d(TAG, "Handler message was added to the queue")
            val value = queue.take()
            when (value) {
                value is Exception -> throw value as Exception
                else -> Log.d(TAG, "${object {}.javaClass.enclosingMethod.name} executed normally")
            }
        } else {
            Log.e(TAG, "Handler message was NOT added to the queue")
        }
    }

    private fun isOnMainThread(): Boolean {
        return Looper.getMainLooper().isCurrentThread
    }

    /**
     * Function to remove the beep sound of voice recognizer.
     */
    private fun muteBeepSoundOfRecorder(shouldMute: Boolean)
    {
        //Beeping appears to occur on STREAM_MUSIC Android stream
        val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val muteValue = if (shouldMute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, muteValue, 0)
    }

    private fun updateLastActionTimestamp() {
        mLastActionTimestamp = Date().time
    }

    private fun throttleAction(): Boolean {
        return Date().time <= mLastActionTimestamp + mTransitionMinimumDelay
    }

    /**
     * Stops voice recognition listening.
     * This method does nothing if voice listening is not active
     */
    fun stopListening() {
        if (!mIsListening) return

        if (throttleAction()) {
            Log.d(TAG, "Hey man calm down! Throttling stop to prevent disaster!")
            return
        }

        mIsListening = false
        updateLastActionTimestamp()
        returnPartialResultsAndRecreateSpeechRecognizer()
    }

    private fun getPartialResultsAsString(): String {
        val out = StringBuilder()

        for (partial in mPartialData) {
            out.append(partial).append(" ")
        }

        if (mUnstableData != null && !mUnstableData!!.isEmpty())
            out.append(mUnstableData)

        return out.toString().trim { it <= ' ' }
    }

    private fun returnPartialResultsAndRecreateSpeechRecognizer() {
        mIsListening = false
        try {
            delegate.onSpeechResult(getPartialResultsAsString())
        } catch (exc: Throwable) {
            Log.e(TAG, "Unhandled exception in delegate onSpeechResult", exc)
        }

        // recreate the speech recognizer
        initSpeechRecognizer(context)
    }

    /**
     * Check if voice recognition is currently active.
     *
     * @return true if the voice recognition is on, false otherwise
     */
    fun isListening(): Boolean {
        return mIsListening
    }

    /**
     * Set whether to only use an offline speech recognition engine.
     * The default is false, meaning that either network or offline recognition engines may be used.
     *
     * @param preferOffline true to prefer offline engine, false to use either one of the two
     * @return speech instance
     */
    fun setPreferOffline(preferOffline: Boolean): SpeechToText {
        mPreferOffline = preferOffline
        return this
    }

    /**
     * Set whether partial results should be returned by the recognizer as the user speaks
     * (default is true). The server may ignore a request for partial results in some or all cases.
     *
     * @param getPartialResults true to get also partial recognition results, false otherwise
     * @return speech instance
     */
    fun setGetPartialResults(getPartialResults: Boolean): SpeechToText {
        mGetPartialResults = getPartialResults
        return this
    }

    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    fun setLocale(locale: Locale): SpeechToText {
        mLocale = locale
        return this
    }

    /**
     * Sets the idle timeout after which the listening will be automatically stopped.
     *
     * @param milliseconds timeout in milliseconds
     * @return speech instance
     */
    fun setStopListeningAfterInactivity(milliseconds: Long): SpeechToText {
        mStopListeningDelayInMs = milliseconds
        initDelayedStopListening(context)
        return this
    }

    /**
     * Sets the minimum interval between start/stop events. This is useful to prevent
     * monkey input from users.
     *
     * @param milliseconds minimum interval between state change in milliseconds
     * @return speech instance
     */
    fun setTransitionMinimumDelay(milliseconds: Long): SpeechToText {
        mTransitionMinimumDelay = milliseconds
        return this
    }
}