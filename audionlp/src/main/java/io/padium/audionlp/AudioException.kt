package io.padium.audionlp

class AudioException : Exception {
    constructor(message: String?, e: Exception?): super(message, e)
    constructor(message: String?): super(message)
    constructor(e: Exception): super(e)
}