package com.guodong.android.jasmine.domain

import androidx.annotation.Keep

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
data class Message(val message: String, val topic: String, val qos: Int = 0)