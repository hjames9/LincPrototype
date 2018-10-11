package io.padium.utils.tcp

import java.lang.Exception

class TcpException : Exception {
    constructor(message: String?, e: Exception?): super(message, e)
    constructor(message: String?): super(message)
    constructor(e: Exception): super(e)
}