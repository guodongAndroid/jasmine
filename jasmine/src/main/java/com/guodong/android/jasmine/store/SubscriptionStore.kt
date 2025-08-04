package com.guodong.android.jasmine.store

import com.guodong.android.jasmine.store.subscription.Subscription
import com.guodong.android.jasmine.store.subscription.SubscriptionNodeTrie

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal interface ISubscriptionStore {
    fun subscribe(subscription: Subscription)
    fun subscribe(clientId: String, topic: String, qos: Int)
    fun unsubscribe(subscription: Subscription)
    fun unsubscribe(clientId: String, topic: String)
    fun unsubscribe(clientId: String)
    fun match(topic: String): List<Subscription>
    fun match(clientId: String, topic: String): List<Subscription>
    fun clear()
}

internal class InMemorySubscriptionStore : ISubscriptionStore {

    private val trie = SubscriptionNodeTrie()

    override fun subscribe(subscription: Subscription) {
        trie.subscribe(subscription)
    }

    override fun subscribe(clientId: String, topic: String, qos: Int) {
        trie.subscribe(clientId, topic, qos)
    }

    override fun unsubscribe(subscription: Subscription) {
        trie.unsubscribe(subscription.clientId, subscription.topic)
    }

    override fun unsubscribe(clientId: String, topic: String) {
        trie.unsubscribe(clientId, topic)
    }

    override fun unsubscribe(clientId: String) {
        trie.unsubscribe(clientId)
    }

    override fun match(topic: String): List<Subscription> {
        return trie.match(topic)
    }

    override fun match(clientId: String, topic: String): List<Subscription> {
        return trie.match(clientId, topic)
    }

    override fun clear() {
        trie.clear()
    }
}