package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.store.IRetainedMessageStore
import com.guodong.android.jasmine.store.ISubscriptionStore
import com.guodong.android.jasmine.store.subscription.Subscription
import com.guodong.android.jasmine.util.validateTopicFilter
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE
import io.netty.handler.codec.mqtt.MqttSubAckPayload
import io.netty.handler.codec.mqtt.MqttSubscribeMessage
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class SubscribeHandler(
    private val forwardHandler: ForwardHandler,
    private val subscriptionStore: ISubscriptionStore,
    private val retainedMessageStore: IRetainedMessageStore,
    private val logger: Logger,
) : MessageHandler<MqttSubscribeMessage> {

    companion object {
        private const val TAG = "SubscribeHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.SUBSCRIBE

    override fun handle(ctx: ChannelHandlerContext, msg: MqttSubscribeMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val topicSubscriptions = msg.payload().topicSubscriptions()

        val topicFilters = topicSubscriptions.map { it.topicFilter() }
        val result = runCatching { topicFilters.forEach { it.validateTopicFilter() } }
        if (result.isFailure) {
            logger.d(
                TAG,
                "SUBSCRIBE -> clientId($clientId), 存在非法的TopicFilter(${topicFilters.joinToString()})",
                result.exceptionOrNull()
            )
            channel.close()
            return
        }

        val qosList = topicSubscriptions.map { it.qualityOfService().value() }
        topicSubscriptions.forEach {
            val topicFilter = it.topicFilter()
            val qos = it.qualityOfService().value()
            val subscription = Subscription(clientId, topicFilter, qos)
            subscriptionStore.subscribe(subscription)
            logger.d(TAG, "SUBSCRIBE -> clientId($clientId), topicFilter($topicFilter), QoS($qos)")
        }

        val subAckMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.SUBACK, false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(msg.variableHeader().messageId()),
            MqttSubAckPayload(qosList)
        )
        channel.writeAndFlush(subAckMessage)

        // 发布保留消息
        topicSubscriptions.forEach {
            val topicFilter = it.topicFilter()
            publishRetainedMessage(channel, topicFilter)
        }
    }

    private fun publishRetainedMessage(
        channel: Channel,
        topicFilter: String,
    ) {
        val messages = retainedMessageStore.match(topicFilter)
        messages.forEach {

            val publishMessage = MqttMessageFactory.newMessage(
                MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(it.qos), true, 0),
                MqttPublishVariableHeader(it.topic, 0),
                channel.alloc().buffer().writeBytes(it.message)
            ) as MqttPublishMessage

            forwardHandler.handle(publishMessage)
        }
    }
}