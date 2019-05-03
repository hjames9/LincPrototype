package io.padium.audionlp.impl

import android.content.Context
import android.util.Log
import io.padium.audionlp.AudioException
import io.padium.audionlp.AudioProcessorLocation
import io.padium.audionlp.AudioTextResult
import io.padium.audionlp.AudioToTextListener
import io.padium.utils.http.HttpMethod
import io.padium.utils.http.HttpUtils
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class WitAudioToTextImpl(@Suppress("UNUSED_PARAMETER") context: Context, private val recording: AtomicBoolean) : AudioToTextImpl {
    companion object {
        private val TAG = WitAudioToTextImpl::class.java.simpleName
        private const val WIT_BASE_URL = "https://api.wit.ai"
        private const val WIT_API_VERSION = "20170307"
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val TRANSFER_ENCODING_HEADER = "Transfer-Encoding"
        private const val TOKEN = "NWFPVCTESQZAVPRXA5AZPK6MW7PYCHDQ"
    }

    //Cloud NLP processing
    private val keepAliveThread = Thread {
        try {
            val keepAliveUrl = "$WIT_BASE_URL/favicon.ico"
            while (keepAliveRunning.get()) {
                val elaspedTime = System.currentTimeMillis() - lastRequestTime.get()
                if (elaspedTime > 30000) {
                    val result = HttpUtils.requestText(keepAliveUrl, HttpMethod.HEAD)
                    val response = result.get(10, TimeUnit.SECONDS)
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
    private val keepAliveRunning = AtomicBoolean(false)
    override val audioQueue = LinkedBlockingQueue<Pair<ShortArray, Int>>()

    init {
        keepAliveRunning.set(true)
        keepAliveThread.start()

        Log.i(TAG, "Initialized WIT speech recognition")
    }

    override fun process(listener: AudioToTextListener, parameters: AudioParameters?) {
        val contentType = "${parameters?.contentType};encoding=${parameters?.encoding};" +
                "bits=${parameters?.encodingSizeValue()};rate=${parameters?.sampleRate};" +
                "endian=${parameters?.endianValue()}"

        val streamer = { stream: OutputStream -> Unit
            try {
                val dos = DataOutputStream(stream)
                while (audioQueue.isNotEmpty() || recording.get()) {
                    val bufferPair = audioQueue.poll(500, TimeUnit.MILLISECONDS)
                    if(null != bufferPair) {
                        for (iter in 0 until bufferPair.second) {
                            dos.writeShort(bufferPair.first[iter].toInt())
                        }
                    }
                }
            } catch(e : InterruptedException) {
            }
        }

        listener.onStart(AudioProcessorLocation.CLOUD)
        val result = processCloudInputStream(streamer, contentType)
        listener.onResult(AudioProcessorLocation.CLOUD, AudioTextResult(result, 0.0, 0.0))
        listener.onEnd(AudioProcessorLocation.CLOUD)
    }

    override fun close() {
        keepAliveRunning.set(false)
        keepAliveThread.interrupt()
        keepAliveThread.join()
    }

    private fun getResponseContentType(headers : Map<String, List<String>>): String? {
        val header = headers["Content-Type"]
        return when(null != header && header.isNotEmpty()) {
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

        val result = HttpUtils.requestBinary(url, HttpMethod.POST, contentType,
                streamer, requestHeaders)

        val response = result.get(10, TimeUnit.SECONDS)

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

        val result = HttpUtils.requestBinary(url, HttpMethod.POST, contentType,
                data, offset, byteCount, requestHeaders)

        val response = result.get(10, TimeUnit.SECONDS)

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