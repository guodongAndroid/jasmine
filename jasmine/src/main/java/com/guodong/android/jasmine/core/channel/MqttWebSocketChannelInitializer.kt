package com.guodong.android.jasmine.core.channel

import com.guodong.android.jasmine.Jasmine
import com.guodong.android.jasmine.common.HTTP_AGGREGATOR_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.HTTP_CODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.HTTP_COMPRESSOR_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.IDLE_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_BROKER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_DECODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_ENCODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.WEBSOCKET_MQTT_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.WEBSOCKET_PROTOCOL_CHANNEL_HANDLER
import com.guodong.android.jasmine.core.codec.MqttWebSocketCodec
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.mqtt.MqttDecoder
import io.netty.handler.codec.mqtt.MqttEncoder
import io.netty.handler.timeout.IdleStateHandler
import java.util.concurrent.TimeUnit

/**
 * Created by guodongAndroid on 2025/9/2
 */
internal class MqttWebSocketChannelInitializer(
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
            .addLast(HTTP_CODER_CHANNEL_HANDLER, HttpServerCodec())
            .addLast(
                HTTP_AGGREGATOR_CHANNEL_HANDLER,
                HttpObjectAggregator(builder.maxContentLength)
            )
            .addLast(HTTP_COMPRESSOR_CHANNEL_HANDLER, HttpContentCompressor())
            .addLast(
                WEBSOCKET_PROTOCOL_CHANNEL_HANDLER,
                WebSocketServerProtocolHandler(
                    builder.wsPath,
                    "mqtt,mqttv3.1,mqttv3.1.1",
                    true,
                    builder.maxContentLength,
                )
            )
            .addLast(WEBSOCKET_MQTT_CHANNEL_HANDLER, MqttWebSocketCodec)
            .addLast(MQTT_DECODER_CHANNEL_HANDLER, MqttDecoder())
            .addLast(MQTT_ENCODER_CHANNEL_HANDLER, MqttEncoder.INSTANCE)
            .addLast(MQTT_BROKER_CHANNEL_HANDLER, mqttHandler)
            .addLast(MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER, exceptionCaughtHandler)
    }
}