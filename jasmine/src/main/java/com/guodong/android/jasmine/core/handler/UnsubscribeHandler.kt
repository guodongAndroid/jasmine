package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.store.ISubscriptionStore
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class UnsubscribeHandler(
    private val subscriptionStore: ISubscriptionStore,
    private val logger: Logger,
) : MessageHandler<MqttUnsubscribeMessage> {

    companion object {
        private const val TAG = "UnsubscribeHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.UNSUBSCRIBE

    override fun handle(ctx: ChannelHandlerContext, msg: MqttUnsubscribeMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val topics = msg.payload().topics()
        topics.forEach {
            subscriptionStore.unsubscribe(clientId, it)
            logger.d(TAG, "UNSUBSCRIBE -> clientId($clientId), topic($it)")
        }

        val unsubAckMessage = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
            MqttMessageIdVariableHeader.from(msg.variableHeader().messageId()), null
        )
        channel.writeAndFlush(unsubAckMessage)
    }
}