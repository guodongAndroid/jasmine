package com.guodong.android.jasmine.core.retry

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.MessageValue
import io.netty.channel.Channel
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.util.AttributeKey
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import io.netty.util.TimerTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class RetryTask(
    private val channel: Channel,
    private val value: MessageValue,
    private val timer: HashedWheelTimer,
    private val intervalMillis: Long,
    private val maxRetries: Int,
    private val logger: Logger,
) : TimerTask {

    companion object {
        private const val TAG = "RetryTask"
    }

    private var timeout: Timeout? = null
    private val retryCount = AtomicInteger(0)
    private val cancelled = AtomicBoolean(false)

    fun start() {
        if (cancelled.get()) {
            return
        }

        timeout = timer.newTimeout(this, intervalMillis, TimeUnit.MILLISECONDS)
    }

    fun cancel() {
        cancelled.set(true)
        timeout?.let {
            if (!it.isExpired && !it.isCancelled) {
                it.cancel()
            }
        }
        timeout = null
    }

    override fun run(timeout: Timeout) {
        if (!channel.isActive || !channel.isRegistered) {
            cancel()
            return
        }

        if (retryCount.incrementAndGet() >= maxRetries) {
            cancel()
            return
        }

        if (cancelled.get()) {
            cancel()
            return
        }

        val clientIdKey = AttributeKey.valueOf<String>(CLIENT_ID)
        val clientId = channel.attr(clientIdKey).get()

        val topic = value.topic
        val qos = value.qos
        val messageId = value.messageId

        val publishMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(
                MqttMessageType.PUBLISH,
                true,
                MqttQoS.valueOf(qos),
                false,
                0
            ),
            MqttPublishVariableHeader(topic, messageId),
            channel.alloc().buffer().writeBytes(value.message),
        )

        logger.d(
            TAG,
            "Retry run -> clientId($clientId), topic($topic), QoS($qos), messageId($messageId)"
        )

        channel.writeAndFlush(publishMessage).addListener {
            start()
        }
    }
}