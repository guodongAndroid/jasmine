package com.guodong.android.jasmine.store

import io.netty.handler.codec.mqtt.MqttPublishMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal data class Session(
    val clientId: String,
    val channelId: String,
    val cleanSession: Boolean,
    val willMessage: MqttPublishMessage? = null,
)

internal interface ISessionStore {
    fun add(session: Session)
    operator fun contains(clientId: String): Boolean
    operator fun get(clientId: String): Session?
    fun remove(clientId: String)
    fun clear()
}

internal class InMemorySessionStore : ISessionStore {

    /**
     * key: clientId
     */
    private val sessions = ConcurrentHashMap<String, Session>()

    override fun add(session: Session) {
        sessions[session.clientId] = session
    }

    override operator fun contains(clientId: String): Boolean {
        return sessions.containsKey(clientId)
    }

    override operator fun get(clientId: String): Session? {
        return sessions[clientId]
    }

    override fun remove(clientId: String) {
        val remove = sessions.remove(clientId)
        if (remove != null) {
            remove.willMessage?.release()
        }
    }

    override fun clear() {
        sessions.clear()
    }
}