package com.guodong.android.jasmine.core.retry

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.common.MessageValue
import io.netty.channel.Channel
import io.netty.util.HashedWheelTimer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal interface RetryGroup {
    fun schedule(channel: Channel, key: MessageKey, value: MessageValue)
    fun remove(key: MessageKey)
    fun remove(clientId: String)
    fun stop()
}

internal class DefaultRetryGroup(
    private val intervalMillis: Long,
    private val maxRetries: Int,
    private val logger: Logger,
) : RetryGroup {

    private var timerRef = AtomicReference<HashedWheelTimer>(null)
    private val tasks = ConcurrentHashMap<MessageKey, RetryTask>()

    override fun schedule(channel: Channel, key: MessageKey, value: MessageValue) {
        val timer = timerRef.updateAndGet { it ?: HashedWheelTimer() }
        tasks.computeIfAbsent(key) {
            RetryTask(channel, value, timer, intervalMillis, maxRetries, logger).also { it.start() }
        }
    }

    override fun remove(key: MessageKey) {
        tasks.remove(key)?.cancel()
    }

    override fun remove(clientId: String) {
        tasks.entries.removeIf {
            val remove = it.key.clientId == clientId
            if (remove) {
                it.value.cancel()
            }
            remove
        }
    }

    override fun stop() {
        tasks.entries.removeIf {
            it.value.cancel()
            true
        }
        tasks.clear()

        timerRef.getAndUpdate {
            it?.stop()
            null
        }
    }
}