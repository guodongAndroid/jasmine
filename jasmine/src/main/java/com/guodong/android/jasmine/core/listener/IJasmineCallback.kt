package com.guodong.android.jasmine.core.listener

import androidx.annotation.Keep
import com.guodong.android.jasmine.Jasmine

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
interface IJasmineCallback {
    fun onStarted(jasmine: Jasmine)
    fun onStartFailure(jasmine: Jasmine, cause: Throwable)
    fun onStopped(jasmine: Jasmine)
    fun onStopFailure(jasmine: Jasmine, cause: Throwable)
}