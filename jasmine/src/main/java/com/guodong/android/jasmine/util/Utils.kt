package com.guodong.android.jasmine.util

import com.guodong.android.jasmine.common.TOPIC_SPLITTER
import com.guodong.android.jasmine.common.TOPIC_WILDCARD_HASH
import com.guodong.android.jasmine.common.TOPIC_WILDCARD_PLUS
import kotlin.random.Random

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal fun String.matchTopic(topic: String): Boolean {
    val segments = this.validateTopicFilter()
    val topicSegments = topic.validateTopicFilter()

    if (segments.size != topicSegments.size) {
        return false
    }

    val union = segments.union(topicSegments).toList()
    return union.size == 1 && union.first() == TOPIC_WILDCARD_PLUS
}

internal fun String.validateTopicFilter(): List<String> {
    if (isEmpty() || isBlank()) {
        throw IllegalArgumentException("非法主题：主题不能为空或者空白字符")
    }

    if (this.length > 65535) {
        throw IllegalArgumentException("非法主题：主题长度不能超过65535")
    }

    if (this.startsWith(TOPIC_SPLITTER)) {
        throw IllegalArgumentException("非法主题：主题不能以 '/' 开头")
    }

    if (this.endsWith(TOPIC_SPLITTER)) {
        throw IllegalArgumentException("非法主题：主题不能以 '/' 结尾")
    }

    val segments = this.split(TOPIC_SPLITTER)
    for ((index, segment) in segments.withIndex()) {
        when {
            segment.contains(TOPIC_WILDCARD_HASH) && segment != TOPIC_WILDCARD_HASH -> throw IllegalArgumentException(
                "非法使用 '#' 通配符：只能单独作为最后一层"
            )

            segment.contains(TOPIC_WILDCARD_PLUS) && segment != TOPIC_WILDCARD_PLUS -> throw IllegalArgumentException(
                "非法使用 '+' 通配符：只能单独作为一层"
            )

            segment == TOPIC_WILDCARD_HASH && index != segments.lastIndex -> throw IllegalArgumentException(
                "'#' 只能在最后一层"
            )

            segment == TOPIC_WILDCARD_PLUS && index == 0 -> throw IllegalArgumentException("非法使用 '+' 通配符：不能作为第一层")
            segment.isEmpty() || segment.isBlank() -> throw IllegalArgumentException("非法主题：主题层级不能为空或者空白字符")
        }
    }

    return segments
}

internal fun randomMessageId() = Random.nextInt(1, 0xffff + 1 /* 65536 */)