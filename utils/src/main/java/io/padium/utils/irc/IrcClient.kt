package io.padium.utils.irc

import java.io.Closeable
import java.net.Socket
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import javax.net.ssl.SSLSocketFactory

class IrcClient(eventHandler: IrcClientEventHandler,
                var nickName: String, val host: String,
                val port: Int = 6667, val tls: Boolean = false) : Closeable {
    companion object {
        private val TAG = IrcClient::class.java.simpleName
        private val Log = Logger.getLogger(TAG)
    }

    private val listener = Runnable {
        Log.info("Starting server read thread for $nickName on $host:$port with tls($tls)")
        try {
            while (running.get()) {
                val serverStr = inFromServer?.readLine()
                serverStr ?: continue
                Log.info("Received from server $serverStr")
                if (serverStr.contains("PING")) {
                    pongNetwork(serverStr.substring(6))
                } else if (serverStr.contains(" PRIVMSG ")) {
                    val main = serverStr.split(" PRIVMSG ")
                    val first = main[0].split("!")
                    val second = main[1].split(":")
                    eventHandler.onMessage(first[0].substring(1).trim(),
                            second[1].trim(), second[0].trim())
                }
            }
        } catch(exc: Exception) {
            Log.finest(exc.message)
        } finally {
            running.set(false)
            Log.info("Stopping server read thread for $nickName on $host:$port with tls($tls)")
        }
    }

    private val running = AtomicBoolean(false)
    private var clientSocket : Socket? = null
    private var outToServer : DataOutputStream? = null
    private var inFromServer : BufferedReader? = null
    private var listenerThread : Thread? = null

    init {
        Log.info("Nickname: $nickName, Host: $host, Port: $port")
    }

    @Throws(IrcClientException::class)
    private fun throwClientConnected() {
        if(isConnected()) {
            throw IrcClientException("IRC client is connected")
        }
    }

    @Throws(IrcClientException::class)
    fun throwClientNotConnected() {
        if(!isConnected()) {
            throw IrcClientException("IRC client is NOT connected")
        }
    }

    @Throws(IrcClientException::class)
    fun open() {
        Log.info("open")
        throwClientConnected()
        try {
            clientSocket = when (tls) {
                true -> {
                    val sslSocketFactory = SSLSocketFactory.getDefault()
                    sslSocketFactory.createSocket(host, port)
                }
                false -> Socket(host, port)
            }

            outToServer = DataOutputStream(clientSocket?.getOutputStream())
            inFromServer = BufferedReader(InputStreamReader(clientSocket?.getInputStream()))
            running.set(true)

            changeNick(nickName)
            userNetwork(nickName)

            listenerThread = Thread(listener)
            listenerThread?.start()
        } catch(exc: Exception) {
            throw IrcClientException(exc.message, exc)
        }
    }

    @Throws(IrcClientException::class)
    override fun close() {
        Log.info("close")
        throwClientNotConnected()
        try {
            quitNetwork("Leaving for now")
            running.set(false)
            clientSocket?.close()
            listenerThread?.join()
            clientSocket = null
            outToServer = null
            inFromServer = null
            listenerThread = null
        } catch(exc: Exception) {
            throw IrcClientException(exc.message, exc)
        }
    }

    fun isConnected(): Boolean {
        return running.get()
    }

    @Throws(IrcClientException::class)
    private fun pongNetwork(message : String) {
        Log.info("pongNetwork")
        throwClientNotConnected()
        outToServer?.writeBytes("PONG $message\r\n")
    }

    @Throws(IrcClientException::class)
    private fun userNetwork(nickName : String) {
        Log.info("userNetwork")
        throwClientNotConnected()
        outToServer?.writeBytes("USER $nickName * * :$nickName Robot\r\n")
    }

    @Throws(IrcClientException::class)
    fun quitNetwork(message : String) {
        Log.info("quitNetwork")
        throwClientNotConnected()
        outToServer?.writeBytes("QUIT :$message\r\n")
    }

    @Throws(IrcClientException::class)
    fun changeNick(nickName : String) {
        Log.info("changeNick")
        throwClientNotConnected()
        this.nickName = nickName
        outToServer?.writeBytes("NICK $nickName\r\n")
    }

    @Throws(IrcClientException::class)
    fun joinChannel(channel : String) {
        Log.info("joinChannel")
        throwClientNotConnected()
        outToServer?.writeBytes("JOIN $channel\r\n")
    }

    @Throws(IrcClientException::class)
    fun leaveChannel(channel : String) {
        Log.info("leaveChannel")
        throwClientNotConnected()
        outToServer?.writeBytes("PART $channel\r\n")
    }

    @Throws(IrcClientException::class)
    fun kickChannel(channel : String, user : String, message : String) {
        Log.info("kickChannel")
        throwClientNotConnected()
        outToServer?.writeBytes("KICK $channel $user :$message\r\n")
    }

    @Throws(IrcClientException::class)
    fun sendMessage(destination : String, message : String) {
        Log.info("sendMessage")
        throwClientNotConnected()
        outToServer?.writeBytes("PRIVMSG $destination :$message\r\n")
    }

    @Throws(IrcClientException::class)
    fun changeMode(destination : String, mode : String) {
        Log.info("changeMode")
        throwClientNotConnected()
        outToServer?.writeBytes("MODE $destination $mode\r\n")
    }

    @Throws(IrcClientException::class)
    fun changeTopic(channel : String, topic : String) {
        Log.info("changeTopic")
        throwClientNotConnected()
        outToServer?.writeBytes("TOPIC $channel :$topic\r\n")
    }

    @Throws(IrcClientException::class)
    fun adhoc(message : String) {
        Log.info("adhoc")
        throwClientNotConnected()
        outToServer?.writeBytes(message)
    }
}
