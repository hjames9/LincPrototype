package io.padium.linc.prototype.http

import android.net.Uri
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.lang.StringBuilder

enum class HttpMethod {
    GET, HEAD, POST, OPTIONS, PUT, PATCH, DELETE
}

object HttpUtils {

    @JvmStatic
    fun buildRequestParameters(parameters: Map<String, String>): String {
        val parametersStr = StringBuilder("?")

        var prefix = ""
        for(parameter in parameters) {
            parametersStr.append(prefix)
            prefix = "&"

            parametersStr.append(Uri.encode(parameter.key, null))
            parametersStr.append('=')
            parametersStr.append(Uri.encode(parameter.value, null))
        }

        return parametersStr.toString()
    }

    @JvmStatic
    fun requestText(url: String, method: HttpMethod, contentType: String, requestBody: String = "",
                    requestHeaders : Map<String, String> = mapOf()): Pair<Int, String?> {
        val mediaType  = MediaType.parse(contentType)
        val body = RequestBody.create(mediaType, requestBody)
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    fun requestBinary(url: String, method: HttpMethod, contentType: String, requestBody: ByteArray = byteArrayOf(),
                      requestHeaders : Map<String, String> = mapOf()): Pair<Int, String?> {
        val mediaType  = MediaType.parse(contentType)
        val body = RequestBody.create(mediaType, requestBody)
        return request(url, method, body, requestHeaders)
    }

    @JvmStatic
    private fun request(url: String, method: HttpMethod, requestBody: RequestBody,
                        requestHeaders : Map<String, String>): Pair<Int, String?> {
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

        return Pair(response.code(), response.body()?.string())
    }
}