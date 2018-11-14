package io.padium.audionlp.impl

import android.media.AudioFormat
import io.padium.audionlp.AudioProcessorLocation
import java.nio.ByteOrder

internal data class AudioParameters(private val processorLocation: AudioProcessorLocation) {
    companion object {
        const val RAW_CONTENT_TYPE = "audio/raw"
        const val ENCODING_SIGNED_INTEGER = "signed-integer"
        const val ENCODING_UNSIGNED_INTEGER = "unsigned-integer"
    }

    var sampleRate = when(processorLocation) {
        AudioProcessorLocation.LOCAL -> 16000
        AudioProcessorLocation.ANY -> 16000
        AudioProcessorLocation.CLOUD -> 8000
    }
    var endian : ByteOrder = ByteOrder.BIG_ENDIAN
    var channel = AudioFormat.CHANNEL_IN_MONO
    val contentType = RAW_CONTENT_TYPE
    var encoding = ENCODING_SIGNED_INTEGER
    var encodingSize = AudioFormat.ENCODING_PCM_16BIT

    fun encodingSizeValue(): Int {
        return when(encodingSize) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_8BIT -> 8
            else -> 8
        }
    }

    fun endianValue(): String {
        return endian.toString().split("_")[0].toLowerCase()
    }

    override fun toString(): String {
        val channelValue = when(channel) {
            AudioFormat.CHANNEL_IN_MONO -> "mono"
            AudioFormat.CHANNEL_IN_STEREO -> "stereo"
            else -> "default"
        }
        return "sampleRate[$sampleRate] endian[${endianValue()}] contentType[$contentType] " +
               "channel[$channelValue] encoding[$encoding] encodingSize[${encodingSizeValue()}]"
    }
}