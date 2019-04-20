package io.padium.linc.prototype.ai.wit

import android.util.Log
import io.padium.utils.http.HttpMethod
import io.padium.utils.http.HttpUtils
import org.json.JSONObject
import java.lang.StringBuilder

class WitUserIntent {
    companion object {
        private val TAG = WitUserIntent::class.java.simpleName
        private const val WIT_BASE_URL = "https://api.wit.ai"
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val FORM_URLENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded"
        private const val OCTET_STREAM_CONTENT_TYPE = "application/octet-stream"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val ACCEPT_HEADER = "Accept"
        private const val TOKEN = "4SD6DALZ7FWZYTORBALV5BGPXV63T4UV"

        private val PHRASES = listOf(
                "What is for dinner?" +
                "What should I make for dinner?" +
                "Let's start dinner" +
                "What can I make really quick?" +
                "Let me see what I can eat" +
                "Damn, I'm fucking hungry" +
                "What's there to eat?" +
                "My stomach is growling" +
                "I'm losing weight" +
                "I feel like I'm going to faint" +
                "Got hunger pains" +
                "Never felt so famished in my life" +
                "I want to munch on something" +
                "Could use some grub right now" +
                "I want to try a new recipe" +
                "I haven't eaten in a while" +
                "Been waiting to chow down" +
                "Haven't had some good chinese food in a while" +
                "Wouldn't mind a slice of pizza right now" +
                "Could use something to fill my belly right now")
    }

    fun processPhrases() {
        for(phrase in PHRASES) {
            val url = "$WIT_BASE_URL/message${HttpUtils.buildRequestParameters(mapOf("q" to phrase))}"
            val response = HttpUtils.requestText(url, HttpMethod.GET, OCTET_STREAM_CONTENT_TYPE,
                    "", mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN"))
            Log.i(TAG, "Sending request to $url got response code ${response.first}, body ${response.second}")
            Thread.sleep(5000)
        }
    }

    fun getUserIntent() {
        val authHeader = mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN")
        Log.i(TAG, HttpUtils.requestText(WIT_BASE_URL, HttpMethod.POST, JSON_CONTENT_TYPE,
                "", authHeader).second)
    }

    fun getEntities(): List<String> {
        val url = "$WIT_BASE_URL/entities"
        val response = HttpUtils.requestText(url, HttpMethod.GET, OCTET_STREAM_CONTENT_TYPE, "",
                mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN"))

        if(HttpUtils.isSuccess(response.first)) {
            val json = JSONObject(response.second)
            Log.i(TAG, "Received json $json")
        }

        return listOf()
    }

    fun getMessage() {
        val url = "$WIT_BASE_URL/message${HttpUtils.buildRequestParameters(mapOf("q" to "set an alarm tomorrow at 7am"))}"
        val response = HttpUtils.requestText(url, HttpMethod.GET, OCTET_STREAM_CONTENT_TYPE,
                "", mapOf(AUTHORIZATION_HEADER to "Bearer $TOKEN"))
        Log.i(TAG, "Sending request to $url got response code ${response.first}, body ${response.second}")

        val str = StringBuilder()
        for(iterator in response.third) {
            str.append(iterator.key)
            str.append("=")

            var comma = ""
            for(value in iterator.value) {
                str.append(comma)
                comma = ", "
                str.append(value)
            }
        }
        Log.i(TAG, str.toString())
    }
}
