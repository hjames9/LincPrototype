package io.padium.utils.irc

import org.junit.Test
import org.junit.Assert.*

class IrcClientTest {

    @Test
    fun ircClient_MakesTLSConnection() {
        ircClient_MakesConnection(true)
    }

    @Test
    fun ircClient_MakesClearConnection() {
        ircClient_MakesConnection(false)
    }

    @Test
    fun ircClient_CloseClosedConnection() {
        try {
            val eventHandler = object : IrcClientEventHandler {
                override fun onMessage(user: String, message: String, destination: String?) {
                }
            }
            val ircClient = IrcClient(eventHandler, "LincTest", "irc.freenode.net")
            assertFalse(ircClient.isConnected())
            ircClient.close()
            fail("IRC client should throw on closing closed connection")
        } catch(exc: IrcClientException) {
        } catch(exc: Exception) {
            fail("IrcClientException should have been thrown")
        }
    }

    @Test
    fun ircClient_OpenOpenedConnection() {
        var ircClient : IrcClient? = null
        try {
            val eventHandler = object : IrcClientEventHandler {
                override fun onMessage(user: String, message: String, destination: String?) {
                }
            }
            ircClient = IrcClient(eventHandler, "LincTest", "irc.freenode.net")
            assertFalse(ircClient.isConnected())
            ircClient.open()
            assertTrue(ircClient.isConnected())
            ircClient.open()
            fail("IRC client should throw on opening opened connection")
        } catch(exc: IrcClientException) {
        } catch(exc: Exception) {
            fail("IrcClientException should have been thrown")
        } finally {
            ircClient?.close()
            assertFalse(ircClient?.isConnected() == true)
        }
    }


    private fun ircClient_MakesConnection(tls: Boolean) {
        var ircClient : IrcClient? = null
        try {
            val port = when(tls) {
                true -> 7000
                false -> 6667
            }
            val nickName = when(tls) {
                true -> "LincTLS"
                false -> "LincClear"
            }
            val eventHandler = object : IrcClientEventHandler {
                override fun onMessage(user: String, message: String, destination: String?) {
                }
            }
            ircClient = IrcClient(eventHandler, nickName, "irc.freenode.net", port, tls)
            assertFalse(ircClient.isConnected())

            ircClient.open()
            assertTrue(ircClient.isConnected())
            ircClient.joinChannel("#padium")
            ircClient.sendMessage("#padium", "Hello from unit test!")
        } catch(exp: Exception) {
            fail("Exception thrown: ${exp.message}")
        } finally {
            ircClient?.close()
            assertFalse(ircClient?.isConnected() == true)
        }
    }
}