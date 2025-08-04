package com.guodong.android.jasmine.common

import androidx.annotation.Keep
import io.netty.handler.codec.mqtt.MqttMessageType
import io.netty.handler.codec.mqtt.MqttQoS
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal data class MessageKey(val clientId: String, val messageId: Int)

internal class MessageValue(
    val messageId: Int,
    val topic: String,
    val qos: Int,
    val message: ByteArray,
) {
    private companion object {
        private val STATE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
            MessageValue::class.java, MqttMessageType::class.java, "state"
        )
    }

    @Keep
    @Volatile
    var state: MqttMessageType = MqttMessageType.PUBLISH
        private set

    fun nextState(): Boolean {
        if (!isExactlyOnce()) {
            return false
        }

        while (true) { // lock-free loop
            val cur = this.state // atomic read
            return when (cur) {
                MqttMessageType.PUBLISH -> {
                    STATE_UPDATER.compareAndSet(
                        this,
                        MqttMessageType.PUBLISH,
                        MqttMessageType.PUBREC
                    )
                }

                MqttMessageType.PUBREC -> {
                    STATE_UPDATER.compareAndSet(
                        this,
                        MqttMessageType.PUBREC,
                        MqttMessageType.PUBREL
                    )
                }

                MqttMessageType.PUBREL -> {
                    STATE_UPDATER.compareAndSet(
                        this,
                        MqttMessageType.PUBREL,
                        MqttMessageType.PUBCOMP
                    )
                }

                else -> false
            }
        }
    }

    fun isExactlyOnce() = qos == MqttQoS.EXACTLY_ONCE.value()
}