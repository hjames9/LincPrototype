package io.padium.audionlp

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.util.Log
import edu.cmu.pocketsphinx.RecognitionListener
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizer
import io.padium.utils.http.HttpMethod
import io.padium.utils.http.HttpUtils
import org.json.JSONObject
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class AudioProcessorLocation {
    LOCAL,
    CLOUD
}

class AudioToText(private val context: Context, private val location : AudioProcessorLocation)
    : Closeable {
    companion object {
        private val TAG = AudioToText::class.java.simpleName

        //Android audio recording
        private const val RECORDER_SAMPLERATE = 8000
        private const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)

        //Cloud NLP processing
        private const val WIT_BASE_URL = "https://api.wit.ai"
        private const val WIT_API_VERSION = "20170307"
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val WAV_CONTENT_TYPE = "audio/wav"
        private const val RAW_CONTENT_TYPE = "audio/raw"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val TRANSFER_ENCODING_HEADER = "Transfer-Encoding"
        private const val TOKEN = "NWFPVCTESQZAVPRXA5AZPK6MW7PYCHDQ"

        //Local NLP process
        private const val SPHINX_ACOUSTIC_MODEL_PATH = "en-us-ptm"
        private const val SPHINX_DICTIONARY_PATH = "cmudict-en-us.dict"
    }

    //Android audio recording
    private lateinit var audioRecord : AudioRecord
    private val buffer = ShortArray(BUFFER_SIZE / 4)

    //Local NLP processing
    private lateinit var recognizer : SpeechRecognizer
    private val recognizerListener = object : RecognitionListener {
        override fun onEndOfSpeech() {
        }

        override fun onError(e: Exception?) {
        }

        override fun onResult(hypothesis: Hypothesis?) {
        }

        override fun onPartialResult(hypothesis: Hypothesis?) {
        }

        override fun onTimeout() {
        }

        override fun onBeginningOfSpeech() {
        }
    }

    //Cloud NLP processing
    private val keepAliveThread = Thread {
        try {
            val keepAliveUrl = "$WIT_BASE_URL/favicon.ico"
            while (running.get()) {
                val elaspedTime = System.currentTimeMillis() - lastRequestTime.get()
                if (elaspedTime > 30000) {
                    val response = HttpUtils.requestText(keepAliveUrl, HttpMethod.HEAD)
                    lastRequestTime.set(System.currentTimeMillis())
                    when {
                        HttpUtils.isSuccess(response.first) -> Log.i(TAG, response.second)
                        HttpUtils.isError(response.first) -> Log.e(TAG, response.second)
                        else -> Log.e(TAG, "Unknown response code ${response.first} and message ${response.second}")
                    }
                }
                Thread.sleep(3000)
            }
        } catch(e : InterruptedException) {
            Log.i(TAG, e.message)
        }
    }
    private var lastRequestTime = AtomicLong(0)
    private val running = AtomicBoolean(false)

    init {
        when(location) {
            AudioProcessorLocation.LOCAL -> {
                recognizer = defaultSetup()
                        .setAcousticModel(File(SPHINX_ACOUSTIC_MODEL_PATH))
                        .setDictionary(File(SPHINX_DICTIONARY_PATH))
                        .recognizer
                recognizer.addListener(recognizerListener)
            }
            AudioProcessorLocation.CLOUD -> {
                //Create initial connection
                running.set(true)
                keepAliveThread.start()
            }
        }
    }

    override fun close() {
        running.set(false)
        keepAliveThread.interrupt()
        keepAliveThread.join()
    }

    @Throws(AudioException::class)
    fun getMicrophoneText(listenTime: Long, timeUnit: TimeUnit): String {
        try {
            if(location == AudioProcessorLocation.LOCAL)
                throw AudioException("Only cloud processing is currently supported")

            audioRecord = AudioRecord(AudioSource.MIC, RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, BUFFER_SIZE)

            val contentType = "$RAW_CONTENT_TYPE;encoding=signed-integer;bits=16;rate=$RECORDER_SAMPLERATE;endian=big"
            val streamer = { stream: OutputStream -> Unit
                val startTime : Long = System.currentTimeMillis() / 1000
                var elaspedTime : Long = 0
                val dos = DataOutputStream(stream)

                audioRecord.startRecording()
                while(timeUnit.toSeconds(listenTime) > elaspedTime) {
                    val result = audioRecord.read(buffer, 0, BUFFER_SIZE / 4, AudioRecord.READ_BLOCKING)
                    when (result) {
                        AudioRecord.ERROR -> throw AudioException("AudioRecord reading error")
                        AudioRecord.ERROR_BAD_VALUE -> throw AudioException("AudioRecord invalid parameters")
                        AudioRecord.ERROR_DEAD_OBJECT -> throw AudioException("AudioRecord not valid anymore")
                        AudioRecord.ERROR_INVALID_OPERATION -> throw AudioException("AudioRecord isn't properly initialized")
                        else -> {
                            for(iter in 0 until result) {
                                dos.writeShort(buffer[iter].toInt())
                            }
                        }
                    }
                    elaspedTime = (System.currentTimeMillis() / 1000) - startTime
                }
                audioRecord.stop()
                dos.close()
            }
            return processCloudInputStream(streamer, contentType)
        } finally {
            if(AudioRecord.RECORDSTATE_RECORDING == audioRecord.recordingState) {
                audioRecord.stop()
            }
            audioRecord.release()
        }
    }

    @Throws(AudioException::class)
    fun getWavFileText(file : File): String {
        return when(location) {
            AudioProcessorLocation.LOCAL -> throw AudioException("Only cloud processing is currently supported")
            AudioProcessorLocation.CLOUD -> processCloudInputStream(Files.readAllBytes(file.toPath()), WAV_CONTENT_TYPE)
        }
    }

    private fun getResponseContentType(headers : Map<String, List<String>>): String? {
        val header = headers["Content-Type"]
        return when(null != header && !header.isEmpty()) {
            true -> header?.get(0)
            false -> ""
        }
    }

    @Throws(AudioException::class)
    private fun processCloudInputStream(streamer: (stream: OutputStream) -> Unit, contentType: String)
            : String {
        val url = "$WIT_BASE_URL/speech${HttpUtils.buildRequestParameters(mapOf("v" to WIT_API_VERSION))}"

        val requestHeaders = mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN",
                TRANSFER_ENCODING_HEADER to "chunked")

        val response = HttpUtils.requestBinary(url, HttpMethod.POST, contentType,
                streamer, requestHeaders)

        return processCloudResponse(response)
    }

    @Throws(AudioException::class)
    private fun processCloudInputStream(fileData : ByteArray, contentType : String): String {
        return processCloudInputStream(fileData, 0, fileData.size, contentType)
    }

    @Throws(AudioException::class)
    private fun processCloudInputStream(data : ByteArray, offset : Int, byteCount: Int,
                                        contentType : String): String {
        val url = "$WIT_BASE_URL/speech${HttpUtils.buildRequestParameters(mapOf("v" to WIT_API_VERSION))}"

        val requestHeaders = mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN")

        val response = HttpUtils.requestBinary(url, HttpMethod.POST, contentType,
                data, offset, byteCount, requestHeaders)

        return processCloudResponse(response)
    }

    @Throws(AudioException::class)
    private fun processCloudResponse(response: Triple<Int, String?, Map<String, List<String>>>): String {
        lastRequestTime.set(System.currentTimeMillis())

        if(HttpUtils.isSuccess(response.first) && JSON_CONTENT_TYPE == getResponseContentType(response.third)) {
            val json = JSONObject(response.second)
            return json.get("_text").toString()
        } else if(HttpUtils.isError(response.first)) {
            throw AudioException("Error accessing cloud with code ${response.first}, response ${response.second}")
        } else {
            throw AudioException("Unknown response accessing cloud with code ${response.first}, response ${response.second}")
        }
    }
}
