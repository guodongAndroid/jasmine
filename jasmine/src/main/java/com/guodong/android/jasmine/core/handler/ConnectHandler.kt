package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.CLIENT_USERNAME
import com.guodong.android.jasmine.common.IDLE_CHANNEL_HANDLER
import com.guodong.android.jasmine.core.channel.ChannelGroup
import com.guodong.android.jasmine.core.auth.IMqttAuthenticator
import com.guodong.android.jasmine.core.auth.MqttAuthentication
import com.guodong.android.jasmine.core.auth.RealMqttAuthenticatorChain
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.store.IPublishMessageStore
import com.guodong.android.jasmine.store.IRetainedMessageStore
import com.guodong.android.jasmine.store.ISessionStore
import com.guodong.android.jasmine.store.ISubscriptionStore
import com.guodong.android.jasmine.store.RetainedMessage
import com.guodong.android.jasmine.store.Session
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader
import io.netty.handler.codec.mqtt.MqttConnectMessage
import io.netty.handler.codec.mqtt.MqttConnectReturnCode
import io.netty.handler.codec.mqtt.MqttFixedHeader
import io.netty.handler.codec.mqtt.MqttMessageFactory
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttPublishMessage
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader
import io.netty.handler.codec.mqtt.MqttQoS
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.AttributeKey
import java.util.concurrent.TimeUnit
import kotlin.math.round

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class ConnectHandler(
    private val channelGroup: ChannelGroup,
    private val retryGroup: RetryGroup,
    private val sessionStore: ISessionStore,
    private val subscriptionStore: ISubscriptionStore,
    private val retainedMessageStore: IRetainedMessageStore,
    private val qos1PublishMessageStore: IPublishMessageStore,
    private val qos2PublishMessageStore: IPublishMessageStore,
    private val authenticators: List<IMqttAuthenticator>,
    private val clientListener: IClientListener?,
    private val logger: Logger,
) : MessageHandler<MqttConnectMessage> {

    companion object {
        private const val TAG = "ConnectHandler"
    }

    override val messageType: MqttMessageType = MqttMessageType.CONNECT

    override fun handle(ctx: ChannelHandlerContext, msg: MqttConnectMessage) {
        val channel = ctx.channel()
        val channelId = channel.id().asLongText()

        val payload = msg.payload()
        val clientId = payload.clientIdentifier()

        // clientId不能为空
        if (clientId.isEmpty() || clientId.isBlank()) {
            val connAckMessage = MqttMessageFactory.newMessage(
                MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttConnAckVariableHeader(
                    MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED,
                    false
                ),
                null,
            )
            channel.writeAndFlush(connAckMessage)
            channel.close()
            return
        }

        // 处理认证
        val username = payload.userName()
        val password = payload.passwordInBytes()
        if (!handleAuthenticate(channel, clientId, username, password)) {
            return
        }

        val variableHeader = msg.variableHeader()

        // 清理上次的回话
        val isCleanSession = variableHeader.isCleanSession
        if (isCleanSession) {
            cleanup(clientId)
        }

        // 处理连接心跳包
        val keepAliveTimeSeconds = variableHeader.keepAliveTimeSeconds()
        if (keepAliveTimeSeconds > 0) {
            if (channel.pipeline().names().contains(IDLE_CHANNEL_HANDLER)) {
                channel.pipeline().remove(IDLE_CHANNEL_HANDLER)
            }
            val expire = round(keepAliveTimeSeconds * 1.5F)
            val idleStateHandler = IdleStateHandler(0L, 0L, expire.toLong(), TimeUnit.SECONDS)
            channel.pipeline().addFirst(IDLE_CHANNEL_HANDLER, idleStateHandler)
        }

        // 处理遗嘱信息
        val willMessage = if (variableHeader.isWillFlag) {
            val willTopic = payload.willTopic()
            val willQos = variableHeader.willQos()
            val willMessageInBytes = payload.willMessageInBytes()
            val isWillRetain = variableHeader.isWillRetain

            // 处理保留遗属消息
            if (isWillRetain) {
                handleWillRetained(clientId, willTopic, willQos, willMessageInBytes)
            }

            MqttMessageFactory.newMessage(
                MqttFixedHeader(
                    MqttMessageType.PUBLISH,
                    false,
                    MqttQoS.valueOf(willQos),
                    isWillRetain,
                    0,
                ),
                MqttPublishVariableHeader(willTopic, 0),
                channel.alloc().buffer().writeBytes(willMessageInBytes),
            ) as MqttPublishMessage
        } else {
            null
        }

        val session = Session(
            clientId,
            channelId,
            isCleanSession,
            willMessage
        )
        sessionStore.add(session)

        channel.attr(AttributeKey.valueOf<String>(CLIENT_ID)).set(clientId)
        channel.attr(AttributeKey.valueOf<String>(CLIENT_USERNAME)).set(username)

        val sessionPresent = sessionStore.contains(clientId) && !isCleanSession
        val ack = MqttMessageFactory.newMessage(
            MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
            MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent),
            null
        )

        logger.d(
            TAG,
            "CONNECT -> clientId($clientId), cleanSession($isCleanSession), clientAddress(${channel.remoteAddress()})"
        )

        channel.writeAndFlush(ack)

        clientListener?.onClientOnline(clientId, username)

        // 如果cleanSession为0, 需要重发同一clientId存储的未完成的QoS1和QoS2的DUP消息
        if (!isCleanSession) {
            val qos1PublishMessages = qos1PublishMessageStore.match(clientId)
            qos1PublishMessages.forEach {
                val (key, value) = it
                val publishMessage = MqttMessageFactory.newMessage(
                    MqttFixedHeader(
                        MqttMessageType.PUBLISH,
                        true,
                        MqttQoS.valueOf(value.qos),
                        false,
                        0
                    ),
                    MqttPublishVariableHeader(value.topic, value.messageId),
                    channel.alloc().buffer().writeBytes(value.message),
                )
                channel.writeAndFlush(publishMessage)

                retryGroup.schedule(channel, key, value)
            }

            val qos2PublishMessages = qos2PublishMessageStore.match(clientId)
            qos2PublishMessages.forEach {
                val (key, value) = it
                val pubRelMessage = MqttMessageFactory.newMessage(
                    MqttFixedHeader(
                        MqttMessageType.PUBREL,
                        true,
                        MqttQoS.valueOf(value.qos),
                        false,
                        0
                    ),
                    MqttMessageIdVariableHeader.from(value.messageId),
                    channel.alloc().buffer().writeBytes(value.message)
                )
                channel.writeAndFlush(pubRelMessage)

                retryGroup.schedule(channel, key, value)
            }
        }
    }

    private fun handleWillRetained(
        clientId: String,
        topicName: String,
        qos: Int,
        messageBytes: ByteArray
    ) {
        if (messageBytes.isEmpty()) {
            retainedMessageStore.remove(topicName)
            return
        }

        val retainedMessage = RetainedMessage(clientId, topicName, qos, messageBytes, true)
        retainedMessageStore.add(retainedMessage)
    }

    private fun handleAuthenticate(
        channel: Channel,
        clientId: String,
        username: String?,
        password: ByteArray?,
    ): Boolean {
        // 必须提供用户名和密码，不允许匿名访问
        if (username == null || password == null) {
            val connAckMessage = MqttMessageFactory.newMessage(
                MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttConnAckVariableHeader(
                    MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    false
                ),
                null,
            )
            channel.writeAndFlush(connAckMessage)
            channel.close()
            return false
        }

        val authentication = MqttAuthentication(clientId, username, password)
        val chain = RealMqttAuthenticatorChain(authentication, authenticators, 0)
        val result = chain.proceed(authentication)
        if (!result.value) {
            val connAckMessage = MqttMessageFactory.newMessage(
                MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttConnAckVariableHeader(
                    MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                    false
                ),
                null,
            )
            channel.writeAndFlush(connAckMessage)
            channel.close()
            return false
        }

        return true
    }

    private fun cleanup(clientId: String) {
        sessionStore[clientId]?.let {
            channelGroup[it.channelId]?.close()
        }
        sessionStore.remove(clientId)
        subscriptionStore.unsubscribe(clientId)
        retainedMessageStore.removeWill(clientId)
        qos1PublishMessageStore.remove(clientId)
        qos2PublishMessageStore.remove(clientId)
    }
}