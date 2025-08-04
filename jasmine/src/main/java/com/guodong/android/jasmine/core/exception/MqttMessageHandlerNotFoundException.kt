package com.guodong.android.jasmine.core.exception

import io.netty.handler.codec.mqtt.MqttMessageType

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class MqttMessageHandlerNotFoundException(
    messageType: MqttMessageType
) : RuntimeException("Do you have registered the message handler for MessageType[$messageType] in the pool?")