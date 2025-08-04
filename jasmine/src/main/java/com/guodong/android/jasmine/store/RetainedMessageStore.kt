package com.guodong.android.jasmine.store

import com.guodong.android.jasmine.util.matchTopic
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal data class RetainedMessage(
    val clientId: String,
    val topic: String,
    val qos: Int,
    val message: ByteArray,
    val isWill: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RetainedMessage

        if (qos != other.qos) return false
        if (topic != other.topic) return false
        if (!message.contentEquals(other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qos
        result = 31 * result + topic.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }
}

internal interface IRetainedMessageStore {
    fun add(message: RetainedMessage)
    operator fun contains(topic: String): Boolean
    operator fun get(topic: String): RetainedMessage?
    fun match(topicFilter: String): List<RetainedMessage>
    fun remove(topic: String)
    fun removeWill(clientId: String)
    fun clear()
}

internal class InMemoryRetainedMessageStore : IRetainedMessageStore {

    private val store = ConcurrentHashMap<String, RetainedMessage>()

    override fun add(message: RetainedMessage) {
        store[message.topic] = message
    }

    override operator fun contains(topic: String): Boolean {
        return store.containsKey(topic)
    }

    override operator fun get(topic: String): RetainedMessage? {
        return store.entries.asSequence()
            .filter { topic.matchTopic(it.key) }
            .firstOrNull()?.value
    }

    override fun match(topicFilter: String): List<RetainedMessage> {
        return store.entries.asSequence()
            .filter { topicFilter.matchTopic(it.key) }
            .map { it.value }.toList()
    }

    override fun remove(topic: String) {
        store.remove(topic)
    }

    override fun removeWill(clientId: String) {
        store.entries.removeIf {
            it.value.isWill && it.value.clientId == clientId
        }
    }

    override fun clear() {
        store.clear()
    }
}