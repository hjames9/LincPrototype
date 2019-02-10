package io.padium.utils.irc

import java.io.Closeable
import java.net.Socket
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class IrcClient(eventHandler: IrcClientEventHandler,
                nickName: String, host: String, port: Int = 6667) : Closeable {
    companion object {
        private val TAG = IrcClient::class.java.simpleName
        private val Log = Logger.getLogger(TAG)
    }

    private val listener = object : Thread() {
        override fun run() {
            while(running.get()) {
                val serverStr = inFromServer.readLine()
                serverStr ?: continue
                Log.info("Received from server $serverStr")
                if(serverStr.contains("PING")) {
                    pongNetwork(serverStr.substring(6))
                } else if(serverStr.contains(" PRIVMSG ")) {
                    val main = serverStr.split(" PRIVMSG ")
                    val first = main[0].split("!")
                    val second = main[1].split(":")
                    eventHandler.onMessage(first[0].substring(1).trim(),
                            second[1].trim(), second[0].trim())
                }
            }
        }
    }

    private val running = AtomicBoolean(true)
    private val clientSocket = Socket(host, port)
    private val outToServer = DataOutputStream(clientSocket.getOutputStream())
    private val inFromServer = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

    init {
        Log.info("Nickname: $nickName, Host: $host, Port: $port")
        changeNick(nickName)
        userNetwork(nickName)
        listener.start()
    }

    override fun close() {
        Log.info("close")
        quitNetwork("Leaving for now")
        running.set(false)
        clientSocket.close()
    }

    fun pongNetwork(message : String) {
        Log.info("pongNetwork")
        outToServer.writeBytes("PONG $message\r\n")
    }

    fun quitNetwork(message : String) {
        Log.info("quitNetwork")
        outToServer.writeBytes("QUIT :$message\r\n")
    }

    fun userNetwork(nickName : String) {
        Log.info("userNetwork")
        outToServer.writeBytes("USER $nickName * * :$nickName Robot\r\n")
    }

    fun changeNick(nickName : String) {
        outToServer.writeBytes("NICK $nickName\r\n")
    }

    fun joinChannel(channel : String) {
        Log.info("joinChannel")
        outToServer.writeBytes("JOIN $channel\r\n")
    }

    fun leaveChannel(channel : String) {
        Log.info("leaveChannel")
        outToServer.writeBytes("PART $channel\r\n")
    }

    fun kickChannel(channel : String, user : String, message : String) {
        Log.info("kickChannel")
        outToServer.writeBytes("KICK $channel $user :$message\r\n")
    }

    fun sendMessage(destination : String, message : String) {
        Log.info("sendMessage")
        outToServer.writeBytes("PRIVMSG $destination :$message\r\n")
    }

    fun changeMode(destination : String, mode : String) {
        Log.info("changeMode")
        outToServer.writeBytes("MODE $destination $mode\r\n")
    }

    fun changeTopic(channel : String, topic : String) {
        Log.info("changeTopic")
        outToServer.writeBytes("TOPIC $channel :$topic\r\n")
    }
}
