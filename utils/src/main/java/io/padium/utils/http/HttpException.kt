package io.padium.utils.http

import java.lang.Exception

class HttpException : Exception {
    constructor(message: String?, e: Exception?): super(message, e)
    constructor(message: String?): super(message)
    constructor(e: Exception): super(e)
}