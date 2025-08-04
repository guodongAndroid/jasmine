package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.common.MessageValue
import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.store.IPublishMessageStore
import com.guodong.android.jasmine.store.IRetainedMessageStore
import com.guodong.android.jasmine.store.RetainedMessage
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.EXACTLY_ONCE
import io.netty.handler.codec.mqtt.MqttQoS.FAILURE
import io.netty.util.AttributeKey
import java.util.concurrent.TimeUnit

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class PublishHandler(
    private val retainedMessageStore: IRetainedMessageStore,
    private val qos1PublishMessageStore: IPublishMessageStore,
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val forwardHandler: ForwardHandler,
    private val logger: Logger,
) : MessageHandler<MqttPublishMessage> {

    companion object {
        private const val TAG = "PublishHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.PUBLISH

    override fun handle(ctx: ChannelHandlerContext, msg: MqttPublishMessage) {
        val channel = ctx.channel()
        val fixedHeader = msg.fixedHeader()
        val qos = fixedHeader.qosLevel()
        val isRetain = fixedHeader.isRetain

        val variableHeader = msg.variableHeader()
        val topicName = variableHeader.topicName()
        val packetId = variableHeader.packetId()

        val payload = msg.payload().duplicate()
        val messageBytes = ByteArray(payload.readableBytes())
        payload.getBytes(payload.readerIndex(), messageBytes)

        val clientIdKey = AttributeKey.valueOf<String>(CLIENT_ID)
        val clientId = channel.attr(clientIdKey).get() ?: return

        // 处理保留消息
        if (isRetain) {
            handleRetained(clientId, topicName, qos.value(), messageBytes)
        }

        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        when (qos) {
            AT_MOST_ONCE -> {
                forwardHandler.handle(msg)
            }

            AT_LEAST_ONCE -> {
                val key = MessageKey(clientId, packetId)
                if (qos1PublishMessageStore.contains(key)) {
                    respPubAck(channel, packetId)
                    return
                }

                val value = MessageValue(
                    packetId,
                    topicName,
                    qos.value(),
                    messageBytes
                )
                qos1PublishMessageStore.put(key, value)

                respPubAck(channel, packetId)

                forwardHandler.handle(msg)

                try {
                    channel.eventLoop().schedule({
                        qos1PublishMessageStore.remove(key)
                    }, 10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    qos1PublishMessageStore.remove(key)
                }
            }

            EXACTLY_ONCE -> {
                val key = MessageKey(clientId, packetId)
                if (qos2PublishMessageStore.contains(key)) {
                    respPubRec(channel, packetId)
                    return
                }

                val value = MessageValue(
                    packetId,
                    topicName,
                    qos.value(),
                    messageBytes,
                ).also { it.nextState() }
                qos2PublishMessageStore.put(key, value)

                respPubRec(channel, packetId)
            }

            FAILURE -> throw IllegalArgumentException("非法的QoS(${qos.value()})")
        }
    }

    private fun handleRetained(
        clientId: String,
        topicName: String,
        qos: Int,
        messageBytes: ByteArray
    ) {
        if (messageBytes.isEmpty()) {
            retainedMessageStore.remove(topicName)
            return
        }

        val retainedMessage = RetainedMessage(clientId, topicName, qos, messageBytes)
        retainedMessageStore.add(retainedMessage)
    }

    private fun respPubAck(channel: Channel, messageId: Int) {
        val pubAckMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(messageId),
            null
        )
        channel.writeAndFlush(pubAckMessage)
    }

    private fun respPubRec(channel: Channel, messageId: Int) {
        val pubRecMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(messageId),
            null
        )
        channel.writeAndFlush(pubRecMessage)
    }
}