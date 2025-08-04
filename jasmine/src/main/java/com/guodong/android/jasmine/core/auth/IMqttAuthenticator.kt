package com.guodong.android.jasmine.core.auth

import androidx.annotation.Keep
import com.guodong.android.jasmine.core.interceptor.Interceptor
import com.guodong.android.jasmine.core.interceptor.RealInterceptorChain

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
class MqttAuthentication(
    val clientId: String,
    val username: String,
    val password: ByteArray,
)

interface IMqttAuthenticator : Interceptor<MqttAuthentication, Boolean> {

    companion object : IMqttAuthenticator {

        private const val TAG = "IMqttAuthenticator"

        override val name: String = TAG

        override fun intercept(
            chain: Interceptor.Chain<MqttAuthentication, Boolean>
        ): Interceptor.Result<MqttAuthentication, Boolean> {
            return Interceptor.Result(this, true, "认证成功")
        }
    }
}

internal class RealMqttAuthenticatorChain(
    request: MqttAuthentication,
    interceptors: List<Interceptor<MqttAuthentication, Boolean>>,
    index: Int,
) : RealInterceptorChain<MqttAuthentication, Boolean>(request, interceptors, index)