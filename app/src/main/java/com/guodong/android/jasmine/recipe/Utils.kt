package com.guodong.android.jasmine.recipe

/**
 * Created by guodongAndroid on 2025/8/4
 */
fun String.safeToInt(default: Int = -1): Int {
    return try {
        toInt()
    } catch (e : NumberFormatException) {
        default
    }
}