package io.padium.utils.http

import io.padium.utils.Utils

import org.apache.commons.codec.net.URLCodec
import kotlin.collections.LinkedHashMap

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient

import java.lang.StringBuilder
import java.net.URL
import java.util.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

enum class HttpMethod {
    GET, HEAD, POST, OPTIONS, PUT, PATCH, DELETE
}

object HttpUtils {
    private const val CONTENT_TYPE = "content-type"

    private val vertx : Vertx
    private val clients = Utils.lruCache<URL, WebClient>()

    init {
        System.setProperty("vertx.disableFileCPResolving", "true")
        System.setProperty("vertx.disableDnsResolver", "true")
        vertx = Vertx.vertx()
    }

    @JvmStatic
    private fun getHttpClient(urlStr: String, ca: String,
                              cert: String, key: String) : WebClient {
        val url = URL(urlStr)
        var client = clients[url]

        if(null == client) {
            val options = HttpClientOptions()

            //Configure TLS
            if (ca.isNotEmpty()) {
                options.isSsl = true
                options.isTrustAll = false
                options.pemTrustOptions = PemTrustOptions().addCertValue(Buffer.buffer(ca))
            }
            if(cert.isNotEmpty() && key.isNotEmpty()) {
                options.isSsl = true
                options.pemKeyCertOptions = PemKeyCertOptions().addCertValue(Buffer.buffer(cert)).addKeyValue(Buffer.buffer(key))
            }
            if("https".equals(url.protocol, ignoreCase = true)) {
                options.isSsl = true
            }

            client = WebClient.wrap(vertx.createHttpClient(options))
            clients[url] = client
        }

        return client!!
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
    fun requestText(urlStr: String, method: HttpMethod, contentType: String = "", requestBody: String = "",
                    requestHeaders : Map<String, String> = mapOf(),
                    ca: String = "", cert: String = "", key: String = ""): Future<Triple<Int, String?, Map<String, List<String>>>> {
        val body = Buffer.buffer(requestBody)
        return request(urlStr, method, contentType, requestHeaders, body, ca, cert, key)
    }

    @JvmStatic
    fun requestBinary(urlStr: String, method: HttpMethod, contentType: String, requestBody: ByteArray = byteArrayOf(),
                      requestHeaders : Map<String, String> = mapOf(),
                      ca: String = "", cert: String = "", key: String = ""): Future<Triple<Int, String?, Map<String, List<String>>>> {
        val body = Buffer.buffer(requestBody)
        return request(urlStr, method, contentType, requestHeaders, body, ca, cert, key)
    }

    @JvmStatic
    fun requestBinary(urlStr: String, method: HttpMethod, contentType: String, requestBody: ByteArray,
                      offset: Int, byteCount: Int, requestHeaders : Map<String, String> = mapOf(),
                      ca: String = "", cert: String = "", key: String = ""):
            Future<Triple<Int, String?, Map<String, List<String>>>> {
        val body = Buffer.buffer(Arrays.copyOfRange(requestBody, offset, offset + byteCount))
        return request(urlStr, method, contentType, requestHeaders, body, ca, cert, key)
    }

    @JvmStatic
    fun requestBinary(urlStr: String, method: HttpMethod, contentType: String,
                      streamer: (stream: OutputStream) -> Unit, requestHeaders : Map<String, String> = mapOf(),
                      ca: String = "", cert: String = "", key: String = ""):
            Future<Triple<Int, String?, Map<String, List<String>>>> {
        val outputStream = ByteArrayOutputStream()
        val body = object : ReadStream<Buffer> {
            override fun endHandler(endHandler: Handler<Void>?): ReadStream<Buffer> {
                return this
            }
            override fun exceptionHandler(handler: Handler<Throwable>?): ReadStream<Buffer> {
                return this
            }
            override fun fetch(amount: Long): ReadStream<Buffer> {
                return this
            }
            override fun handler(handler: Handler<Buffer>?): ReadStream<Buffer> {
                streamer(outputStream)
                handler?.handle(Buffer.buffer(outputStream.toByteArray()))
                outputStream.reset()
                return this
            }
            override fun pause(): ReadStream<Buffer> {
                return this
            }
            override fun resume(): ReadStream<Buffer> {
                return this
            }
        }
        return request(urlStr, method, contentType, requestHeaders, body, ca, cert, key)
    }


    @JvmStatic
    private fun request(urlStr: String, method: HttpMethod, contentType: String,
                        requestHeaders : Map<String, String>, requestBody: Any,
                        ca: String, cert: String, key: String): Future<Triple<Int, String?, Map<String, List<String>>>> {
        val vertxMethod = when(method) {
            HttpMethod.GET -> io.vertx.core.http.HttpMethod.GET
            HttpMethod.HEAD -> io.vertx.core.http.HttpMethod.HEAD
            HttpMethod.OPTIONS -> io.vertx.core.http.HttpMethod.OPTIONS
            HttpMethod.POST -> io.vertx.core.http.HttpMethod.POST
            HttpMethod.PUT -> io.vertx.core.http.HttpMethod.PUT
            HttpMethod.PATCH -> io.vertx.core.http.HttpMethod.PATCH
            HttpMethod.DELETE -> io.vertx.core.http.HttpMethod.DELETE
        }

        val future = CompletableFuture<Triple<Int, String?, Map<String, List<String>>>>()
        val client = getHttpClient(urlStr, ca, cert, key)
        val url = URL(urlStr)
        val port = when(url.port) {-1 -> url.defaultPort else -> url.port}

        val request = client.request(vertxMethod, port, url.host, url.path)
        request.putHeader(CONTENT_TYPE, contentType)
        requestHeaders.forEach {
            request.putHeader(it.key, it.value)
        }

        val responseHandler : (AsyncResult<HttpResponse<Buffer>>) -> Unit = { response ->
            if(response.succeeded()) {
                val result = response.result()
                val headers = result.headers()
                val headersMap = mutableMapOf<String, MutableList<String>>()
                headers.forEach {
                    var list = headersMap[it.key]
                    if(null == list) {
                        list = ArrayList()
                        headersMap[it.key] = list
                    }
                    list.add(it.value)
                }
                future.complete(Triple(result.statusCode(), result.body().toString(),
                        headersMap))
            } else {
                future.completeExceptionally(response.cause())
            }
        }

        if(requestBody is Buffer) {
            if (requestBody.bytes.isEmpty()) {
                request.send(responseHandler)
            } else {
                request.sendBuffer(requestBody, responseHandler)
            }
        } else if(requestBody is ReadStream<*>) {
            @Suppress("UNCHECKED_CAST")
            request.sendStream(requestBody as ReadStream<Buffer>, responseHandler)
        }
        return future
    }
}
