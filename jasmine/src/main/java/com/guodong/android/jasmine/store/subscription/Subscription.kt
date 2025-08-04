package com.guodong.android.jasmine.store.subscription

import io.netty.handler.codec.mqtt.MqttQoS

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal data class Subscription(
    val clientId: String,
    val topic: String,
    val qos: Int = MqttQoS.AT_MOST_ONCE.value(),
)
