package com.guodong.android.jasmine.core.channel

import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.common.CLIENT_ID
import com.guodong.android.jasmine.common.CLIENT_USERNAME
import com.guodong.android.jasmine.core.handler.WillMessageHandler
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.listener.IClientListener.ClientOfflineReason.Companion.EXCEPTION_OCCURRED
import com.guodong.android.jasmine.store.ISessionStore
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey
import java.io.IOException

/**
 * Created by guodongAndroid on 2025/8/1
 *
 * 异常处理器一般放到处理器管道最后以便处理所有异常
 */
@Sharable
internal class ExceptionCaughtHandler(
    private val willMessageHandler: WillMessageHandler,
    private val clientListener: IClientListener?,
    private val sessionStore: ISessionStore,
    private val logger: Logger,
) : ChannelInboundHandlerAdapter() {

    companion object {
        private const val TAG = "ExceptionCaughtHandler"
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        val channel = ctx.channel()
        val clientIdKey = AttributeKey.valueOf<String>(CLIENT_ID)
        val clientId = channel.attr(clientIdKey).get() ?: null

        logger.e(TAG, "exceptionCaught: ${cause?.message}", cause)

        if (cause is IOException) {
            if (clientId != null && sessionStore.contains(clientId)) {
                val userNameKey = AttributeKey.valueOf<String>(CLIENT_USERNAME)
                val userName = channel.attr(userNameKey).get() ?: ""

                willMessageHandler.handle(clientId)
                sessionStore.remove(clientId)

                clientListener?.onClientOffline(clientId, userName, EXCEPTION_OCCURRED)
            }
        }

        ctx.close()
    }
}