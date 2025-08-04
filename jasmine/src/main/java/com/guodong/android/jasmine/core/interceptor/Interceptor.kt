package com.guodong.android.jasmine.core.interceptor

import androidx.annotation.Keep

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
interface Interceptor<Req, Resp> {

    val name: String

    fun intercept(chain: Chain<Req, Resp>): Result<Req, Resp>

    interface Chain<Req, Resp> {

        fun request(): Req

        fun proceed(req: Req): Result<Req, Resp>
    }

    data class Result<Req, Resp>(
        val interceptor: Interceptor<Req, Resp>,
        val value: Resp,
        val desc: String,
    )
}

internal open class RealInterceptorChain<Req, Resp>(
    private val request: Req,
    private val interceptors: List<Interceptor<Req, Resp>>,
    private val index: Int,
) : Interceptor.Chain<Req, Resp> {

    private fun copy(request: Req, index: Int) =
        RealInterceptorChain(request, interceptors, index)

    override fun request(): Req = request

    override fun proceed(req: Req): Interceptor.Result<Req, Resp> {
        check(index < interceptors.size)

        val next = copy(req, index + 1)
        val interceptor = interceptors[index]

        return interceptor.intercept(next)
    }
}