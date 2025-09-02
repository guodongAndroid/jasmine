package com.guodong.android.jasmine.core.exception

import androidx.annotation.Keep
import com.guodong.android.jasmine.Jasmine

/**
 * Created by guodongAndroid on 2025/9/2
 */
@Keep
class IllegalJasmineStateException(
    @param:Jasmine.State val state: Int,
    desc: String? = null,
) : IllegalStateException(desc ?: "Illegal jasmine state: $state") {

    override val message: String
        get() = super.message!!
}