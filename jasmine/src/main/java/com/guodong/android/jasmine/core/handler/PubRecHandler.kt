package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.store.IPublishMessageStore
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class PubRecHandler(
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val logger: Logger,
) : MessageHandler<MqttMessage> {

    companion object {
        private const val TAG = "PubRecHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.PUBREC

    override fun handle(ctx: ChannelHandlerContext, msg: MqttMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val variableHeader = msg.variableHeader() as MqttMessageIdVariableHeader
        val messageId = variableHeader.messageId()

        logger.d(TAG, "PUBREC -> clientId($clientId), messageId($messageId)")

        val key = MessageKey(clientId, messageId)
        val value = qos2PublishMessageStore.get(key)
        if (value != null && value.nextState()) {
            respPubRel(channel, messageId)
            return
        }

        respPubRel(channel, messageId)
    }

    private fun respPubRel(channel: Channel, messageId: Int) {
        val pubRelMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(messageId),
            null,
        )
        channel.writeAndFlush(pubRelMessage)
    }
}