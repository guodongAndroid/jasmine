package com.guodong.android.jasmine.core.allocator

import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class MessageIdAllocator {

    companion object {
        private const val MIN_MESSAGE_ID = 1
        private const val MAX_MESSAGE_ID = 65535
    }

    private val allocator = AtomicInteger(MIN_MESSAGE_ID)

    fun alloc(): Int {
        return allocator.getAndUpdate {
            if (it >= MAX_MESSAGE_ID) {
                MIN_MESSAGE_ID
            } else {
                it + 1
            }
        }
    }
}