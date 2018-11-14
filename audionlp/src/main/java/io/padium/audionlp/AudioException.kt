package io.padium.audionlp

open class AudioException : Exception {
    constructor(message: String?, e: Exception?): super(message, e)
    constructor(message: String?): super(message)
    constructor(e: Exception): super(e)
}