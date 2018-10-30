package io.padium.utils.http

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.apache.commons.codec.net.URLCodec
import java.io.OutputStream
import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

enum class HttpMethod {
    GET, HEAD, POST, OPTIONS, PUT, PATCH, DELETE
}

object HttpUtils {
    private val client : OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
        builder.connectTimeout(0, TimeUnit.SECONDS)
        builder.readTimeout(0, TimeUnit.SECONDS)
        builder.writeTimeout(0, TimeUnit.SECONDS)
        //val tlsConfig = buildTlsFactory()
        //builder.sslSocketFactory(tlsConfig.first, tlsConfig.second)
        //TODO: Add client side TLS
        client = builder.build()
    }

    @JvmStatic
    private fun buildTlsFactory(): Pair<SSLSocketFactory, X509TrustManager> {
        val sslContext = SSLContext.getInstance("TLS")
        val tmf = TrustManagerFactory.getInstance("TLS")
        val x509TrustManager = tmf.trustManagers[0] as X509TrustManager

        return Pair<SSLSocketFactory, X509TrustManager>(sslContext.socketFactory,
                x509TrustManager)
    }

    @JvmStatic
    fun isInformational(code: Int): Boolean {
        return IntRange(100, 199).contains(code)
    }

    @JvmStatic
    fun isSuccess(code: Int): Boolean {
        return IntRange(200, 299).contains(code)
    }

    @JvmStatic
    fun isRedirect(code: Int): Boolean {
        return IntRange(300, 399).contains(code)
    }

    @JvmStatic
    fun isClientError(code: Int): Boolean {
        return IntRange(400, 499).contains(code)
    }

    @JvmStatic
    fun isServerError(code: Int): Boolean {
        return IntRange(500, 599).contains(code)
    }

    @JvmStatic
    fun isError(code: Int): Boolean {
        return isClientError(code) || isServerError(code)
    }

    @JvmStatic
    fun buildRequestParameters(parameters: Map<String, String>): String {
        val parametersStr = StringBuilder("?")
        val coder = URLCodec()

        var prefix = ""
        for(parameter in parameters) {
            parametersStr.append(prefix)
            prefix = "&"

            parametersStr.append(coder.encode(parameter.key))
            parametersStr.append('=')
            parametersStr.append(coder.encode(parameter.value))
        }

        return parametersStr.toString()
    }

    @JvmStatic
    fun requestText(url: String, method: HttpMethod, contentType: String = "", requestBody: String = "",
                    requestHeaders : Map<String, String> = mapOf()): Triple<Int, String?, Map<String, List<String>>> {
        val mediaType  = MediaType.parse(contentType)
        val body = RequestBody.create(mediaType, requestBody)
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    fun requestBinary(url: String, method: HttpMethod, contentType: String, requestBody: ByteArray = byteArrayOf(),
                      requestHeaders : Map<String, String> = mapOf()): Triple<Int, String?, Map<String, List<String>>> {
        val mediaType  = MediaType.parse(contentType)
        val body = RequestBody.create(mediaType, requestBody)
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    fun requestBinary(url: String, method: HttpMethod, contentType: String, requestBody: ByteArray,
                      offset: Int, byteCount: Int, requestHeaders : Map<String, String> = mapOf()):
            Triple<Int, String?, Map<String, List<String>>> {
        val mediaType  = MediaType.parse(contentType)
        val body = RequestBody.create(mediaType, requestBody, offset, byteCount)
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    fun requestBinary(url: String, method: HttpMethod, contentType: String,
                      streamer: (stream: OutputStream) -> Unit,
                      requestHeaders : Map<String, String> = mapOf()):
            Triple<Int, String?, Map<String, List<String>>> {
         val body = object : RequestBody() {
            override fun contentType(): MediaType? {
                return MediaType.parse(contentType)
            }
            override fun writeTo(sink: BufferedSink) {
                streamer(sink.outputStream())
            }
        }
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    private fun request(url: String, method: HttpMethod, requestBody: RequestBody,
                        requestHeaders : Map<String, String>):
            Triple<Int, String?, Map<String, List<String>>> {
        //Build request with method
        val request = Request.Builder().url(url)
        when(method) {
            HttpMethod.GET -> request.get()
            HttpMethod.HEAD -> request.head()
            HttpMethod.OPTIONS -> request.method(HttpMethod.OPTIONS.toString(), requestBody)
            HttpMethod.POST -> request.post(requestBody)
            HttpMethod.PUT -> request.put(requestBody)
            HttpMethod.PATCH -> request.patch(requestBody)
            HttpMethod.DELETE -> request.delete(requestBody)
        }

        //Request headers
        for(requestHeader in requestHeaders)
            request.addHeader(requestHeader.key, requestHeader.value)

        val response = client.newCall(request.build()).execute()

        val headersMap = mutableMapOf<String, List<String>>()
        val headers = response.headers()
        for(iterator in 0 until headers.size()) {
            val headerName = headers.name(iterator)
            headersMap[headerName] = headers.values(headerName)
        }

        return Triple(response.code(), response.body()?.string(), headersMap)
    }
}