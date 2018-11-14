package io.padium.audionlp.impl

import io.padium.audionlp.AudioToTextListener
import java.io.Closeable
import java.util.concurrent.LinkedBlockingQueue

internal interface AudioToTextImpl : Closeable {
    val audioQueue : LinkedBlockingQueue<Pair<ShortArray, Int>>
    fun process(listener: AudioToTextListener, parameters: AudioParameters)
}