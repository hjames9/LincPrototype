package io.padium.audionlp.wav

import io.padium.audionlp.AudioException

internal class WavFileException : AudioException {
    constructor(message: String?, e: AudioException?): super(message, e)
    constructor(message: String?): super(message)
    constructor(e: Exception): super(e)

}