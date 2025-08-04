package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.common.MessageValue
import com.guodong.android.jasmine.store.IPublishMessageStore
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.util.AttributeKey
import java.util.concurrent.TimeUnit

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class PubRelHandler(
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val forwardHandler: ForwardHandler,
    private val logger: Logger,
) : MessageHandler<MqttMessage> {

    companion object {
        private const val TAG = "PubRelHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.PUBREL

    override fun handle(ctx: ChannelHandlerContext, msg: MqttMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val variableHeader = msg.variableHeader() as MqttMessageIdVariableHeader
        val messageId = variableHeader.messageId()

        logger.d(TAG, "PUBREL -> clientId($clientId), messageId($messageId)")

        val key = MessageKey(clientId, messageId)
        val value = qos2PublishMessageStore.get(key)

        if (value != null && value.nextState()) {
            respPubComp(variableHeader, channel)
            forward(channel, key, value)
            return
        }

        respPubComp(variableHeader, channel)
    }

    private fun forward(channel: Channel, key: MessageKey, value: MessageValue) {
        val publishMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(
                MqttMessageType.PUBLISH,
                false,
                MqttQoS.valueOf(value.qos),
                false,
                0
            ),
            MqttPublishVariableHeader(value.topic, value.messageId),
            channel.alloc().buffer().writeBytes(value.message),
        ) as MqttPublishMessage

        forwardHandler.handle(publishMessage)

        try {
            channel.eventLoop().schedule({
                qos2PublishMessageStore.remove(key)
            }, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            qos2PublishMessageStore.remove(key)
        }
    }

    private fun respPubComp(
        variableHeader: MqttMessageIdVariableHeader,
        channel: Channel
    ) {
        val pubCompMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(variableHeader.messageId()),
            null,
        )
        channel.writeAndFlush(pubCompMessage)
    }
}