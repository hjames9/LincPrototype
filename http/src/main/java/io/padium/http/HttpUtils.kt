package io.padium.http

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.codec.net.URLCodec
import java.lang.StringBuilder

enum class HttpMethod {
    GET, HEAD, POST, OPTIONS, PUT, PATCH, DELETE
}

object HttpUtils {

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
    fun requestText(url: String, method: HttpMethod, contentType: String, requestBody: String = "",
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
    private fun request(url: String, method: HttpMethod, requestBody: RequestBody,
                        requestHeaders : Map<String, String>): Triple<Int, String?, Map<String, List<String>>> {
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

        val client = OkHttpClient()
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
