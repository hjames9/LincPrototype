package io.padium.utils.irc

interface IrcClientEventHandler {
    fun onMessage(user: String, message: String, destination: String?)
}
