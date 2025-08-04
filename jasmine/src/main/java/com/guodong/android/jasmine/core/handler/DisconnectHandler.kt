package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.CLIENT_USERNAME
import com.guodong.android.jasmine.common.MANUAL_DISCONNECT_KEY
import com.guodong.android.jasmine.common.UNKNOWN_CLIENT_USERNAME
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.listener.IClientListener.ClientOfflineReason.Companion.MANUAL_DISCONNECT
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.store.IPublishMessageStore
import com.guodong.android.jasmine.store.ISessionStore
import com.guodong.android.jasmine.store.ISubscriptionStore
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class DisconnectHandler(
    private val retryGroup: RetryGroup,
    private val sessionStore: ISessionStore,
    private val subscriptionStore: ISubscriptionStore,
    private val qos1PublishMessageStore: IPublishMessageStore,
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val clientListener: IClientListener?,
    private val logger: Logger,
) : MessageHandler<MqttMessage> {

    companion object {
        private const val TAG = "DisconnectHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.DISCONNECT

    override fun handle(ctx: ChannelHandlerContext, msg: MqttMessage) {
        val channel = ctx.channel()
        val clientId = channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).get() ?: return
        val session = sessionStore[clientId] ?: return
        if (session.cleanSession) {
            subscriptionStore.unsubscribe(clientId)
            qos1PublishMessageStore.remove(clientId)
            qos2PublishMessageStore.remove(clientId)
        }
        logger.d(TAG, "DISCONNECT -> clientId($clientId), cleanSession(${session.cleanSession})")

        sessionStore.remove(clientId)

        retryGroup.remove(clientId)

        val username = channel.attr(AttributeKey.valueOf<String>(CLIENT_USERNAME)).get()
            ?: UNKNOWN_CLIENT_USERNAME

        clientListener?.onClientOffline(clientId, username, MANUAL_DISCONNECT)

        channel.attr(AttributeKey.valueOf<Boolean>(MANUAL_DISCONNECT_KEY)).set(true)

        channel.close()
    }
}