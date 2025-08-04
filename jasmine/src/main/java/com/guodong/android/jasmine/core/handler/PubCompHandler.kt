package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.MessageKey
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.store.IPublishMessageStore
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class PubCompHandler(
    private val retryGroup: RetryGroup,
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val logger: Logger,
) : MessageHandler<MqttMessage> {

    companion object {
        private const val TAG = "PubCompHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.PUBCOMP

    override fun handle(ctx: ChannelHandlerContext, msg: MqttMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val variableHeader = msg.variableHeader() as MqttMessageIdVariableHeader
        val messageId = variableHeader.messageId()
        logger.d(TAG, "PUBCOMP -> clientId($clientId), messageId($messageId)")

        val key = MessageKey(clientId, messageId)
        retryGroup.remove(key)
        qos2PublishMessageStore.remove(key)
    }
}