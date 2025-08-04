package com.guodong.android.jasmine.core.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.mqtt.MqttMessage
import io.netty.handler.codec.mqtt.MqttMessageType

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal interface MessageHandler<out Message : MqttMessage> {

    val messageType: MqttMessageType

    fun handle(ctx: ChannelHandlerContext, msg: @UnsafeVariance Message)
}