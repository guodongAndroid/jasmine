package com.guodong.android.jasmine.core.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Sharable
internal object MqttWebSocketCodec : MessageToMessageCodec<BinaryWebSocketFrame, ByteBuf>() {

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        out.add(BinaryWebSocketFrame(msg.retain()))
    }

    override fun decode(
        ctx: ChannelHandlerContext,
        msg: BinaryWebSocketFrame,
        out: MutableList<Any>
    ) {
        out.add(msg.retain().content())
    }
}