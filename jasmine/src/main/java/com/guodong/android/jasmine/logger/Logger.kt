package com.guodong.android.jasmine.logger

import android.util.Log
import androidx.annotation.Keep

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
interface Logger {
    fun debuggable(debuggable: Boolean)

    fun d(tag: String, msg: String, cause: Throwable? = null)

    fun v(tag: String, msg: String, cause: Throwable? = null)

    fun w(tag: String, msg: String, cause: Throwable? = null)

    fun i(tag: String, msg: String, cause: Throwable? = null)

    fun e(tag: String, msg: String, cause: Throwable? = null)
}

internal class DefaultLogger(private var debuggable: Boolean) : Logger {

    override fun debuggable(debuggable: Boolean) {
        this.debuggable = debuggable
    }

    override fun d(tag: String, msg: String, cause: Throwable?) {
        if (debuggable) {
            Log.d(tag, msg, cause)
        }
    }

    override fun v(tag: String, msg: String, cause: Throwable?) {
        if (debuggable) {
            Log.v(tag, msg, cause)
        }
    }

    override fun w(tag: String, msg: String, cause: Throwable?) {
        if (debuggable) {
            Log.w(tag, msg, cause)
        }
    }

    override fun i(tag: String, msg: String, cause: Throwable?) {
        if (debuggable) {
            Log.i(tag, msg, cause)
        }
    }

    override fun e(tag: String, msg: String, cause: Throwable?) {
        if (debuggable) {
            Log.e(tag, msg, cause)
        }
    }
}