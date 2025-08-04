package com.guodong.android.jasmine.store

import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.common.MessageValue
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal interface IPublishMessageStore {
    fun put(key: MessageKey, value: MessageValue)
    operator fun contains(key: MessageKey): Boolean
    fun get(key: MessageKey): MessageValue?
    fun match(clientId: String): List<Pair<MessageKey, MessageValue>>
    fun remove(key: MessageKey)
    fun remove(clientId: String)
    fun clear()
}

internal class InMemoryPublishMessageStore : IPublishMessageStore {

    /**
     * key: [MessageKey]
     * key: [MessageValue]
     */
    private val store = ConcurrentHashMap<MessageKey, MessageValue>()

    override fun put(key: MessageKey, value: MessageValue) {
        store[key] = value
    }

    override fun contains(key: MessageKey): Boolean {
        return get(key) != null
    }

    override fun get(key: MessageKey): MessageValue? {
        return store[key]
    }

    override fun match(clientId: String): List<Pair<MessageKey, MessageValue>> {
        return store.entries.filter { it.key.clientId == clientId }.map { it.key to it.value }
    }

    override fun remove(key: MessageKey) {
        store.remove(key)
    }

    override fun remove(clientId: String) {
        store.entries.removeIf { it.key.clientId == clientId }
    }

    override fun clear() {
        store.clear()
    }
}