package com.guodong.android.jasmine.store.subscription

import com.guodong.android.jasmine.common.TOPIC_ROOT_SEGMENT
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal data class SubscriptionNode(

    /**
     * 节点所在的字符
     */
    val segment: String,

    /**
     * 父节点引用（用于反向清理空节点）
     */
    val parent: SubscriptionNode? = null,

    /**
     * 普通子节点 key: topic segment
     */
    val children: ConcurrentHashMap<String, SubscriptionNode> = ConcurrentHashMap(),

    /**
     * 节点保存的客户端信息 key: clientId
     */
    val subscribers: ConcurrentHashMap<String, Subscription> = ConcurrentHashMap(),
) {
    companion object {
        internal val ROOT = SubscriptionNode(TOPIC_ROOT_SEGMENT)
    }
}

internal fun SubscriptionNode.canRemove(): Boolean {
    return subscribers.isEmpty()
            && children.isEmpty()
}

internal fun SubscriptionNode.clearRecursively() {
    subscribers.clear()
    children.values.forEach { it.clearRecursively() }
    children.clear()
}