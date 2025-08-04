package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.MESSAGE_ID_ALLOCATOR_KEY
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.common.MessageValue
import com.guodong.android.jasmine.core.channel.ChannelGroup
import com.guodong.android.jasmine.core.allocator.MessageIdAllocator
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.domain.Message
import com.guodong.android.jasmine.store.IPublishMessageStore
import com.guodong.android.jasmine.store.ISessionStore
import com.guodong.android.jasmine.store.ISubscriptionStore
import com.guodong.android.jasmine.util.randomMessageId
import io.netty.channel.Channel
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.EXACTLY_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.FAILURE
import io.netty.handler.codec.mqtt.MqttQoS.valueOf
import io.netty.util.AttributeKey
import kotlin.math.min

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class ForwardHandler(
    private val channelGroup: ChannelGroup,
    private val retryGroup: RetryGroup,
    private val sessionStore: ISessionStore,
    private val subscriptionStore: ISubscriptionStore,
    private val qos1PublishMessageStore: IPublishMessageStore,
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val logger: Logger,
) {

    companion object {
        private const val TAG = "ForwardHandler"
    }

    fun handle(message: MqttPublishMessage) {
        val fixedHeader = message.fixedHeader()
        val qos = fixedHeader.qosLevel()
        val isDup = fixedHeader.isDup
        val isRetain = fixedHeader.isRetain

        val variableHeader = message.variableHeader()
        val topicName = variableHeader.topicName()

        val payload = message.payload().duplicate()
        val messageBytes = ByteArray(payload.readableBytes())
        payload.getBytes(payload.readerIndex(), messageBytes)

        forward(topicName, qos, isDup, isRetain, messageBytes)
    }

    fun handle(message: Message) {
        forward(
            topicName = message.topic,
            qos = valueOf(message.qos),
            isDup = false,
            isRetain = false,
            payload = message.message.toByteArray(),
        )
    }

    fun handle(clientId: String, message: Message) {
        forward(
            topicName = message.topic,
            qos = valueOf(message.qos),
            isDup = false,
            isRetain = false,
            payload = message.message.toByteArray(),
            clientId = clientId,
        )
    }

    private fun forward(
        topicName: String,
        qos: MqttQoS,
        isDup: Boolean,
        isRetain: Boolean,
        payload: ByteArray,
        clientId: String? = null,
    ) {
        val subscriptions = if (clientId != null) {
            subscriptionStore.match(clientId, topicName)
        } else {
            subscriptionStore.match(topicName)
        }

        for (subscription in subscriptions) {
            val targetClientId = subscription.clientId
            val targetChannelId = sessionStore[targetClientId]?.channelId ?: continue
            val targetChannel = channelGroup[targetChannelId] ?: continue

            // 订阅者收到MQTT消息的QoS级别, 最终取决于发布消息的QoS和主题订阅的QoS
            val forwardQoS = MqttQoS.valueOf(min(subscription.qos, qos.value()))

            realForward(
                forwardQoS,
                topicName,
                targetClientId,
                targetChannel,
                isDup,
                isRetain,
                payload
            )
        }
    }

    private fun realForward(
        forwardQoS: MqttQoS,
        topicName: String,
        targetClientId: String,
        targetChannel: Channel,
        isDup: Boolean,
        isRetain: Boolean,
        payload: ByteArray
    ) {
        when (forwardQoS) {
            AT_MOST_ONCE -> {
                forwardAtMostOnce(
                    forwardQoS,
                    topicName,
                    targetClientId,
                    targetChannel,
                    isDup,
                    isRetain,
                    payload,
                )
            }

            AT_LEAST_ONCE -> {
                forwardAtLeastOnce(
                    forwardQoS,
                    topicName,
                    targetClientId,
                    targetChannel,
                    isDup,
                    isRetain,
                    payload,
                )
            }

            EXACTLY_ONCE -> {
                forwardExactlyOnce(
                    forwardQoS,
                    topicName,
                    targetClientId,
                    targetChannel,
                    isDup,
                    isRetain,
                    payload,
                )
            }

            FAILURE -> {
                throw IllegalArgumentException("非法的QoS(${forwardQoS.value()})")
            }
        }
    }

    private fun forwardAtMostOnce(
        forwardQoS: MqttQoS,
        topicName: String,
        targetClientId: String,
        targetChannel: Channel,
        isDup: Boolean,
        isRetain: Boolean,
        payload: ByteArray,
    ) {
        if (!targetChannel.isActive || !targetChannel.isRegistered) {
            return
        }

        val publishMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, isDup, forwardQoS, isRetain, 0),
            MqttPublishVariableHeader(topicName, 0),
            targetChannel.alloc().buffer().writeBytes(payload)
        )

        logger.d(
            TAG,
            "PUBLISH -> clientId($targetClientId), topic($topicName), QoS(${forwardQoS.value()})"
        )

        targetChannel.writeAndFlush(publishMessage)
    }

    private fun forwardAtLeastOnce(
        forwardQoS: MqttQoS,
        topicName: String,
        targetClientId: String,
        targetChannel: Channel,
        isDup: Boolean,
        isRetain: Boolean,
        payload: ByteArray,
    ) {
        if (!targetChannel.isActive || !targetChannel.isRegistered) {
            return
        }

        val messageId = allocMessageId(targetChannel)

        val publishMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, isDup, forwardQoS, isRetain, 0),
            MqttPublishVariableHeader(topicName, messageId),
            targetChannel.alloc().buffer().writeBytes(payload)
        )

        logger.d(
            TAG,
            "PUBLISH -> clientId($targetClientId), topic($topicName), QoS(${forwardQoS.value()}), messageId($messageId)"
        )

        targetChannel.writeAndFlush(publishMessage)

        val key = MessageKey(targetClientId, messageId)
        val value = MessageValue(messageId, topicName, forwardQoS.value(), payload)
        qos1PublishMessageStore.put(key, value)

        retryGroup.schedule(targetChannel, key, value)
    }

    private fun forwardExactlyOnce(
        forwardQoS: MqttQoS,
        topicName: String,
        targetClientId: String,
        targetChannel: Channel,
        isDup: Boolean,
        isRetain: Boolean,
        payload: ByteArray,
    ) {
        if (!targetChannel.isActive || !targetChannel.isRegistered) {
            return
        }

        val messageId = allocMessageId(targetChannel)

        val publishMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBLISH, isDup, forwardQoS, isRetain, 0),
            MqttPublishVariableHeader(topicName, messageId),
            targetChannel.alloc().buffer().writeBytes(payload)
        )

        logger.d(
            TAG,
            "PUBLISH -> clientId($targetClientId), topic($topicName), QoS(${forwardQoS.value()}), messageId($messageId)"
        )

        targetChannel.writeAndFlush(publishMessage)

        val key = MessageKey(targetClientId, messageId)
        val value = MessageValue(
            messageId,
            topicName,
            forwardQoS.value(),
            payload,
        )
        qos2PublishMessageStore.put(key, value)

        retryGroup.schedule(targetChannel, key, value)
    }

    private fun allocMessageId(channel: Channel): Int {
        val messageIdAllocatorKey =
            AttributeKey.valueOf<MessageIdAllocator>(MESSAGE_ID_ALLOCATOR_KEY)
        val allocator = channel.attr(messageIdAllocatorKey).get()
            ?: return randomMessageId()
        return allocator.alloc()
    }
}