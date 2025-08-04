package com.guodong.android.jasmine.core.handler

import com.guodong.android.jasmine.store.ISessionStore

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class WillMessageHandler(
    private val sessionStore: ISessionStore,
    private val forwardHandler: ForwardHandler,
) {

    fun handle(clientId: String) {
        val willMessage = sessionStore[clientId]?.willMessage ?: return
        forwardHandler.handle(willMessage)
    }
}