package com.guodong.android.jasmine.store.subscription

import com.guodong.android.jasmine.common.TOPIC_WILDCARD_HASH
import com.guodong.android.jasmine.common.TOPIC_WILDCARD_PLUS
import com.guodong.android.jasmine.util.validateTopicFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by guodongAndroid on 2025/8/1
 */
typealias TopicSegment = List<String>

internal class SubscriptionNodeTrie {

    private val root = SubscriptionNode.ROOT

    /**
     * 客户端订阅的所有TopicSegment索引
     */
    private val clientTopicIndex = ConcurrentHashMap<String, CopyOnWriteArraySet<TopicSegment>>()

    internal fun subscribe(clientId: String, topic: String, qos: Int) {
        subscribe(Subscription(clientId, topic, qos))
    }

    internal fun subscribe(subscription: Subscription) {
        val (clientId, topic) = subscription
        val segments = topic.validateTopicFilter()

        var node = root
        for (segment in segments) {
            node = node.children.computeIfAbsent(segment) {
                SubscriptionNode(segment, node)
            }
        }
        node.subscribers[clientId] = subscription

        clientTopicIndex.computeIfAbsent(clientId) { CopyOnWriteArraySet() }.add(segments)
    }

    internal fun unsubscribe(clientId: String, topic: String) {
        val segments = topic.validateTopicFilter()
        var node: SubscriptionNode? = root
        for (segment in segments) {
            node = node?.children?.get(segment) ?: return // 路径不存在
        }

        // 移除订阅者
        node?.subscribers?.remove(clientId)

        val segmentSet = clientTopicIndex[clientId]
        segmentSet?.remove(segments)
        if (segmentSet?.isEmpty() == true) {
            clientTopicIndex.remove(clientId)
        }

        cleanup(node)
    }

    internal fun unsubscribe(clientId: String) {
        /* fun recurse(node: SubscribeNode) {
            // 删除当前节点上的订阅
            node.subscribers.remove(clientId)

            val iterator = node.children.iterator()
            while (iterator.hasNext()) {
                val (_, child) = iterator.next()
                recurse(child)

                if (child.canRemove()) {
                    iterator.remove()
                }
            }
        }

        recurse(root) */

        val segmentSet = clientTopicIndex.remove(clientId) ?: return
        for (segment in segmentSet) {
            removeSegment(root, clientId, segment)
        }
    }

    internal fun match(topic: String): List<Subscription> {
        val segments = topic.validateTopicFilter()
        val result = mutableListOf<Subscription>()

        fun recurse(node: SubscriptionNode, depth: Int) {
            // 终止条件：已遍历所有段
            if (depth == segments.size) {
                result.addAll(node.subscribers.values)
                return
            }

            val segment = segments[depth]
            val nextDepth = depth + 1

            // 匹配普通节点
            node.children[segment]?.let { recurse(it, nextDepth) }

            // 匹配单层通配符+
            node.children[TOPIC_WILDCARD_PLUS]?.let { recurse(it, nextDepth) }

            // 匹配多层通配符#
            node.children[TOPIC_WILDCARD_HASH]?.let { result.addAll(it.subscribers.values) }
        }

        recurse(root, 0)
        return result
    }

    internal fun match(clientId: String, topic: String): List<Subscription> {
        val segments = topic.validateTopicFilter()
        val result = mutableListOf<Subscription>()

        fun recurse(node: SubscriptionNode, depth: Int) {
            // 终止条件：已遍历所有段
            if (depth == segments.size) {
                result.addAll(node.subscribers.filter { it.key == clientId }.values)
                return
            }

            val segment = segments[depth]
            val nextDepth = depth + 1

            // 匹配普通节点
            node.children[segment]?.let { recurse(it, nextDepth) }

            // 匹配单层通配符+
            node.children[TOPIC_WILDCARD_PLUS]?.let { recurse(it, nextDepth) }

            // 匹配多层通配符#
            node.children[TOPIC_WILDCARD_HASH]?.let {
                val values = it.subscribers.filter { subscriber ->
                    subscriber.key == clientId
                }.values

                result.addAll(values)
            }
        }

        recurse(root, 0)
        return result
    }

    internal fun clear() {
        clientTopicIndex.clear()
        root.clearRecursively()
    }

    private fun removeSegment(node: SubscriptionNode?, clientId: String, segments: TopicSegment) {
        var current: SubscriptionNode? = node
        for (segment in segments) {
            current = current?.children?.get(segment) ?: return
        }

        // 移除订阅者
        current?.subscribers?.remove(clientId)

        // 向上递归清理空节点
        cleanup(current)
    }

    private fun cleanup(node: SubscriptionNode?) {
        var current: SubscriptionNode? = node
        while (current != null && current.canRemove()) {
            val parent = current.parent
            parent?.children?.remove(current.segment)
            current = parent
        }
    }
}