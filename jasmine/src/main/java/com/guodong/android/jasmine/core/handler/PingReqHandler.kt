package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.store.ISessionStore
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class PingReqHandler(
    private val sessionStore: ISessionStore,
    private val logger: Logger,
) : MessageHandler<MqttMessage> {

    companion object {
        private const val TAG = "PingReqHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.PINGREQ

    override fun handle(ctx: ChannelHandlerContext, msg: MqttMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return

        if (clientId in sessionStore) {
            logger.d(TAG, "PINGRESP -> clientId($clientId)")
            channel.writeAndFlush(MqttMessage.PINGRESP)
        }
    }
}