package com.guodong.android.jasmine.common

/**
 * Created by guodongAndroid on 2025/8/1
 */

/**
 * 保存在通道中客户端ID的KEY
 */
internal const val CLIENT_ID = "client_id"
internal const val CLIENT_USERNAME = "client_user_name"
internal const val UNKNOWN_CLIENT_USERNAME = "unknown_username"
internal const val MANUAL_DISCONNECT_KEY = "manual_disconnect"
internal const val MESSAGE_ID_ALLOCATOR_KEY = "message_id_allocator"

internal const val TOPIC_WILDCARD_HASH = "#"
internal const val TOPIC_WILDCARD_PLUS = "+"
internal const val TOPIC_SPLITTER = "/"
internal const val TOPIC_ROOT_SEGMENT = "@"

internal const val IDLE_CHANNEL_HANDLER = "idle"
internal const val SSL_CHANNEL_HANDLER = "ssl"
internal const val MQTT_DECODER_CHANNEL_HANDLER = "mqtt-decoder"
internal const val MQTT_ENCODER_CHANNEL_HANDLER = "mqtt-encoder"
internal const val MQTT_BROKER_CHANNEL_HANDLER = "mqtt-broker"
internal const val MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER = "mqtt-exception-caught"

internal const val HTTP_CODER_CHANNEL_HANDLER = "http-coder"
internal const val HTTP_AGGREGATOR_CHANNEL_HANDLER = "http-aggregator"
internal const val HTTP_COMPRESSOR_CHANNEL_HANDLER = "http-compressor"

internal const val WEBSOCKET_PROTOCOL_CHANNEL_HANDLER = "websocket-protocol"
internal const val WEBSOCKET_MQTT_CHANNEL_HANDLER = "websocket-mqtt"