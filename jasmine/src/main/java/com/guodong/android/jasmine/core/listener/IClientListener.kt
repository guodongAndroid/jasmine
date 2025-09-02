package com.guodong.android.jasmine.core.listener

import androidx.annotation.IntDef
import androidx.annotation.Keep
import androidx.annotation.WorkerThread

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
@WorkerThread
interface IClientListener {

    @Keep
    @IntDef(
        ClientOfflineReason.KEEP_ALIVE_TIMEOUT,
        ClientOfflineReason.EXCEPTION_OCCURRED,
        ClientOfflineReason.MANUAL_DISCONNECT,
    )
    annotation class ClientOfflineReason {
        @Keep
        companion object {
            const val MANUAL_DISCONNECT = 1
            const val KEEP_ALIVE_TIMEOUT = 2
            const val EXCEPTION_OCCURRED = 3
        }
    }

    fun onClientOnline(clientId: String, username: String)
    fun onClientOffline(
        clientId: String,
        username: String,
        @ClientOfflineReason reason: Int,
        cause: Throwable? = null
    )
}