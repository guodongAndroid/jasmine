package com.guodong.android.jasmine.core.channel

import android.util.Log
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.CLIENT_USERNAME
import com.guodong.android.jasmine.common.MANUAL_DISCONNECT_KEY
import com.guodong.android.jasmine.common.MESSAGE_ID_ALLOCATOR_KEY
import com.guodong.android.jasmine.core.allocator.MessageIdAllocator
import com.guodong.android.jasmine.core.exception.MqttMessageHandlerNotFoundException
import com.guodong.android.jasmine.core.handler.MessageHandler
import com.guodong.android.jasmine.core.handler.WillMessageHandler
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.listener.IClientListener.ClientOfflineReason.Companion.EXCEPTION_OCCURRED
import com.guodong.android.jasmine.core.listener.IClientListener.ClientOfflineReason.Companion.KEEP_ALIVE_TIMEOUT
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.store.ISessionStore
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader
import io.netty.handler.codec.mqtt.MqttConnectReturnCode
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttIdentifierRejectedException
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageType.CONNACK
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.AttributeKey

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Sharable
internal class MqttHandler(
    private val channelGroup: ChannelGroup,
    private val retryGroup: RetryGroup,
    private val willMessageHandler: WillMessageHandler,
    private val handlers: List<MessageHandler<MqttMessage>>,
    private val sessionStore: ISessionStore,
    private val clientListener: IClientListener?,
    private val logger: Logger,
) : SimpleChannelInboundHandler<MqttMessage>() {

    companion object {
        private const val TAG = "MqttHandler"
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        channelGroup.add(channel)
        val messageIdAllocatorKey =
            AttributeKey.valueOf<MessageIdAllocator>(MESSAGE_ID_ALLOCATOR_KEY)
        channel.attr(messageIdAllocatorKey).setIfAbsent(MessageIdAllocator())
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        val clientIdKey = AttributeKey.valueOf<String>(CLIENT_ID)
        val clientId = channel.attr(clientIdKey).get()
        if (clientId != null && sessionStore.contains(clientId)) {
            sessionStore.remove(clientId)

            val manualDisconnectKey = AttributeKey.valueOf<Boolean>(MANUAL_DISCONNECT_KEY)
            val manualDisconnect = channel.attr(manualDisconnectKey).get() ?: false

            if (!manualDisconnect) {
                willMessageHandler.handle(clientId)
                retryGroup.remove(clientId)

                val userNameKey = AttributeKey.valueOf<String>(CLIENT_USERNAME)
                val userName = channel.attr(userNameKey).get() ?: ""

                clientListener?.onClientOffline(clientId, userName, EXCEPTION_OCCURRED)
            }
        }

        channelGroup.remove(channel)
        super.channelInactive(ctx)
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: MqttMessage) {
        if (!validateMessage(ctx, msg)) {
            return
        }

        val messageType = msg.fixedHeader().messageType()
        val handler = handlers.find { it.messageType == messageType }
        if (handler == null) {
            logger.e(
                TAG,
                "找不到消息处理器",
                MqttMessageHandlerNotFoundException(messageType)
            )
            return
        }

        handler.handle(ctx, msg)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        if (evt !is IdleStateEvent) {
            super.userEventTriggered(ctx, evt)
            return
        }

        if (evt.state() != IdleState.ALL_IDLE) {
            return
        }

        val channel = ctx.channel()

        val clientIdKey = AttributeKey.valueOf<String>(CLIENT_ID)
        if (!channel.hasAttr(clientIdKey)) {
            ctx.close()
            return
        }

        val clientId = channel.attr(clientIdKey).get()
        if (!sessionStore.contains(clientId)) {
            return
        }

        val usernameKey = AttributeKey.valueOf<String>(CLIENT_USERNAME)
        val username = channel.attr(usernameKey).get() ?: ""

        willMessageHandler.handle(clientId)
        retryGroup.remove(clientId)
        sessionStore.remove(clientId)

        ctx.close()

        Log.e(TAG, "userEventTriggered: clientId($clientId), username($username) 心跳超时断开连接")

        clientListener?.onClientOffline(clientId, username, KEEP_ALIVE_TIMEOUT)
    }

    private fun validateMessage(
        ctx: ChannelHandlerContext,
        msg: MqttMessage,
    ): Boolean {
        val decoderResult = msg.decoderResult()
        if (decoderResult.isSuccess) {
            return true
        }

        val connAckMessage = when (decoderResult.cause()) {
            is MqttUnacceptableProtocolVersionException -> {
                MqttMessageFactory.newMessage(
                    MqttFixedHeader(
                        CONNACK,
                        false,
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        0
                    ),
                    MqttConnAckVariableHeader(
                        MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION,
                        false
                    ),
                    null,
                )
            }

            is MqttIdentifierRejectedException -> {
                MqttMessageFactory.newMessage(
                    MqttFixedHeader(
                        CONNACK,
                        false,
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        0
                    ),
                    MqttConnAckVariableHeader(
                        MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED,
                        false
                    ),
                    null,
                )
            }

            else -> null
        }

        connAckMessage?.let { ctx.writeAndFlush(it) }
        ctx.close()
        return false
    }
}