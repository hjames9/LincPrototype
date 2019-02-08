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
                Log.info("Received from server $serverStr")
                if("PING".contains(serverStr)) {
                    val pong = "PONG ${serverStr.substring(6)}"
                    outToServer.writeBytes(pong)
                    Log.info("Received $serverStr and sent $pong")
                } else if(" PRIVMSG ".contains(serverStr)) {
                    val main = serverStr.split(" PRIVMSG ")
                    val first = main[0].split("!")
                    val second = main[1].split(":")
                    eventHandler.onMessage(first[0].substring(1).trim(),
                            second[1].trim(), second[0].trim())
                }
                Thread.sleep(300)
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
        outToServer.writeBytes("USER $nickName * * :$nickName Robot\r\n")
        listener.start()
    }

    override fun close() {
        Log.info("close")
        running.set(false)
        clientSocket.close()
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
