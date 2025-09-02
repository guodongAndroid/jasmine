package com.guodong.android.jasmine.core.channel

import com.guodong.android.jasmine.Jasmine
import com.guodong.android.jasmine.common.IDLE_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_BROKER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_DECODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_ENCODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.mqtt.MqttDecoder
import io.netty.handler.codec.mqtt.MqttEncoder
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

/**
 * Created by guodongAndroid on 2025/9/2
 */
internal class MqttChannelInitializer(
    jasmine: Jasmine,
    private val sslEnabled: Boolean,
) : ChannelInitializer<SocketChannel>() {

    private val builder = jasmine.builder
    private val sslContext = jasmine.sslContext
    private val mqttHandler = jasmine.mqttHandler
    private val exceptionCaughtHandler = jasmine.exceptionCaughtHandler

    override fun initChannel(ch: SocketChannel) {
        ch.pipeline()
            .apply {
                if (sslEnabled) {
                    sslContext?.let {
                        addLast(it.newHandler(ch.alloc()))
                    }
                }
            }
            .addLast(
                IDLE_CHANNEL_HANDLER,
                IdleStateHandler(
                    0L,
                    0L,
                    builder.keepAliveTimeSeconds.toLong(),
                    TimeUnit.SECONDS
                )
            )
            .addLast(
                MQTT_DECODER_CHANNEL_HANDLER,
                MqttDecoder(builder.maxContentLength, builder.maxClientIdLength)
            )
            .addLast(MQTT_ENCODER_CHANNEL_HANDLER, MqttEncoder.INSTANCE)
            .addLast(MQTT_BROKER_CHANNEL_HANDLER, mqttHandler)
            .addLast(MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER, exceptionCaughtHandler)
    }
}