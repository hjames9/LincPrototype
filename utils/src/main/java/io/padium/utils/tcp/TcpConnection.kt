package io.padium.utils.tcp

import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetSocket
import java.io.Closeable

enum class TcpStatus {
    CONNECTED,
    DISCONNECTED
}

class TcpConnection(private val callback: TcpCallback) : Closeable {
    var state = TcpStatus.DISCONNECTED
    private lateinit var socket : NetSocket

    fun write(byteArray: ByteArray) {
        if(state == TcpStatus.CONNECTED) {
            socket.write(Buffer.buffer(byteArray))
        } else {
            callback.onError(this, TcpException("Socket is disconnected"))
        }
    }

    override fun close() {
        socket.close()
    }

    internal fun setupSocket(socket : NetSocket) {
        this.socket = socket
        state = TcpStatus.CONNECTED

        socket.handler { buffer ->
            callback.onRead(this, buffer.bytes)
        }

        socket.exceptionHandler {th ->
            callback.onError(this, th)
        }

        socket.closeHandler {
            state = TcpStatus.DISCONNECTED
            callback.onClose(this)
        }

        socket.writeHandler{
            callback.onWrite(this)
        }
    }
}