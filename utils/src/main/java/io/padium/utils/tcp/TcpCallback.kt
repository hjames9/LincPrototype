package io.padium.utils.tcp

interface TcpCallback {
    fun onRead(connection: TcpConnection, byteArray: ByteArray)
    fun onWrite(connection: TcpConnection)
    fun onError(connection: TcpConnection, th : Throwable)
    fun onConnect(connection: TcpConnection, success: Boolean)
    fun onClose(connection: TcpConnection)
}