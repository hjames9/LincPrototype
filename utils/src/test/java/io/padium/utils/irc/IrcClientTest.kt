package io.padium.utils.irc

import org.junit.Test
import org.junit.Assert.*

class IrcClientTest {

    @Test
    fun ircClient_MakesTLSConnection() {
        try {
            val eventHandler = object : IrcClientEventHandler {
                override fun onMessage(user: String, message: String, destination: String?) {
                }
            }
            val ircClient = IrcClient(eventHandler, "LincTLS", "irc.freenode.net", 7000, true)
            ircClient.joinChannel("#padium")
            ircClient.sendMessage("#padium", "Hello from unit test!")
            Thread.sleep(20000)
        } catch(exp: Exception) {
            fail("Exception thrown: ${exp.message}")
        }
    }
}