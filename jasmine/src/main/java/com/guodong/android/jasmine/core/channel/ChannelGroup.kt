package com.guodong.android.jasmine.core.channel

import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal interface ChannelGroup {

    operator fun get(channelId: String): Channel?

    fun add(channel: Channel): Boolean

    fun remove(channelId: String): Boolean

    fun remove(channel: Channel): Boolean

    operator fun contains(channel: Channel): Boolean

    fun isEmpty(): Boolean

    fun clear()

    fun close()
}

internal class DefaultChannelGroup : ChannelGroup {

    private val channels = ConcurrentHashMap<String, Channel>()
    private val remover = ChannelFutureListener {
        remove(it.channel().id().asLongText())
    }

    override operator fun get(channelId: String): Channel? {
        return channels[channelId]
    }

    override fun add(channel: Channel): Boolean {
        val added = channels.putIfAbsent(channel.id().asLongText(), channel) == null
        if (added) {
            channel.closeFuture().addListeners(remover)
        }

        return added
    }

    override fun remove(channelId: String): Boolean {
        val channel = channels.remove(channelId) ?: return false
        channel.closeFuture().removeListeners(remover)
        return true
    }

    override fun remove(channel: Channel): Boolean {
        return remove(channel.id().asLongText())
    }

    override operator fun contains(channel: Channel): Boolean {
        return channels.containsValue(channel)
    }

    override fun isEmpty(): Boolean {
        return channels.isEmpty()
    }

    override fun clear() {
        channels.clear()
    }

    override fun close() {
        val iterator = channels.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.close().sync()
            iterator.remove()
        }
    }
}