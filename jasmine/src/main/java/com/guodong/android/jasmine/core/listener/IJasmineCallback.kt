package com.guodong.android.jasmine.core.listener

import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import com.guodong.android.jasmine.Jasmine

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
@WorkerThread
interface IJasmineCallback {
    fun onStarting(jasmine: Jasmine)
    fun onStarted(jasmine: Jasmine)
    fun onStartFailure(jasmine: Jasmine, cause: Throwable)
    fun onStopping(jasmine: Jasmine)
    fun onStopped(jasmine: Jasmine)
    fun onStopFailure(jasmine: Jasmine, cause: Throwable)
}