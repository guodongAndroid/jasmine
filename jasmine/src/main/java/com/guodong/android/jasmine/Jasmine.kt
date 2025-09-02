package com.guodong.android.jasmine

import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.annotation.Keep
import com.guodong.android.jasmine.core.auth.IMqttAuthenticator
import com.guodong.android.jasmine.core.channel.ChannelGroup
import com.guodong.android.jasmine.core.channel.DefaultChannelGroup
import com.guodong.android.jasmine.core.channel.ExceptionCaughtHandler
import com.guodong.android.jasmine.core.channel.MqttChannelInitializer
import com.guodong.android.jasmine.core.channel.MqttHandler
import com.guodong.android.jasmine.core.channel.MqttWebSocketChannelInitializer
import com.guodong.android.jasmine.core.handler.ConnectHandler
import com.guodong.android.jasmine.core.handler.DisconnectHandler
import com.guodong.android.jasmine.core.handler.ForwardHandler
import com.guodong.android.jasmine.core.handler.PingReqHandler
import com.guodong.android.jasmine.core.handler.PubAckHandler
import com.guodong.android.jasmine.core.handler.PubCompHandler
import com.guodong.android.jasmine.core.handler.PubRecHandler
import com.guodong.android.jasmine.core.handler.PubRelHandler
import com.guodong.android.jasmine.core.handler.PublishHandler
import com.guodong.android.jasmine.core.handler.SubscribeHandler
import com.guodong.android.jasmine.core.handler.UnsubscribeHandler
import com.guodong.android.jasmine.core.handler.WillMessageHandler
import com.guodong.android.jasmine.core.listener.IClientListener
import com.guodong.android.jasmine.core.listener.IJasmineCallback
import com.guodong.android.jasmine.core.retry.DefaultRetryGroup
import com.guodong.android.jasmine.core.retry.RetryGroup
import com.guodong.android.jasmine.domain.Message
import com.guodong.android.jasmine.logger.DefaultLogger
import com.guodong.android.jasmine.logger.Logger
import com.guodong.android.jasmine.store.IPublishMessageStore
import com.guodong.android.jasmine.store.IRetainedMessageStore
import com.guodong.android.jasmine.store.ISessionStore
import com.guodong.android.jasmine.store.ISubscriptionStore
import com.guodong.android.jasmine.store.InMemoryPublishMessageStore
import com.guodong.android.jasmine.store.InMemoryRetainedMessageStore
import com.guodong.android.jasmine.store.InMemorySessionStore
import com.guodong.android.jasmine.store.InMemorySubscriptionStore
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Created by guodongAndroid on 2025/8/1
 */
@Keep
class Jasmine private constructor(internal val builder: Builder) {

    companion object {
        private const val TAG = "Jasmine"

        private val stateLock = ByteArray(0)
    }

    @IntDef(
        State.IDLE,
        State.STARTING,
        State.RUNNING,
        State.STOPPING,
    )
    @Retention(AnnotationRetention.SOURCE)
    @Keep
    annotation class State {
        companion object {
            internal const val IDLE = 1
            internal const val STARTING = 2
            internal const val RUNNING = 3
            internal const val STOPPING = 4
        }
    }

    private val logger = builder.logger

    private val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
    private val workerGroup: EventLoopGroup =
        NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 4)

    @Volatile
    private var mqttChannel: Channel? = null

    @Volatile
    private var mqttSSLChannel: Channel? = null

    @Volatile
    private var websocketChannel: Channel? = null

    @Volatile
    private var websocketSSLChannel: Channel? = null

    private val channelGroup: ChannelGroup = DefaultChannelGroup()
    private val retryGroup: RetryGroup =
        DefaultRetryGroup(builder.retryIntervalMillis, builder.maxRetries, logger)

    private val sessionStore: ISessionStore = InMemorySessionStore()
    private val subscriptionStore: ISubscriptionStore = InMemorySubscriptionStore()
    private val retainedMessageStore: IRetainedMessageStore = InMemoryRetainedMessageStore()
    private val qos1PublishMessageStore: IPublishMessageStore =
        InMemoryPublishMessageStore()
    private val qos2PublishMessageStore: IPublishMessageStore =
        InMemoryPublishMessageStore()

    private val forwardHandler = ForwardHandler(
        channelGroup,
        retryGroup,
        sessionStore,
        subscriptionStore,
        qos1PublishMessageStore,
        qos2PublishMessageStore,
        logger
    )

    private val connectHandler: ConnectHandler = ConnectHandler(
        channelGroup,
        retryGroup,
        sessionStore,
        subscriptionStore,
        retainedMessageStore,
        qos1PublishMessageStore,
        qos2PublishMessageStore,
        if (builder.authenticators.isEmpty()) listOf(IMqttAuthenticator) else builder.authenticators,
        builder.clientListener,
        logger
    )

    private val disconnectHandler: DisconnectHandler = DisconnectHandler(
        builder.sslEnabled,
        retryGroup,
        sessionStore,
        subscriptionStore,
        qos1PublishMessageStore,
        qos2PublishMessageStore,
        builder.clientListener,
        logger
    )

    private val pingReqHandler: PingReqHandler = PingReqHandler(sessionStore, logger)

    private val pubAckHandler: PubAckHandler =
        PubAckHandler(retryGroup, qos1PublishMessageStore, logger)

    private val pubCompHandler: PubCompHandler =
        PubCompHandler(retryGroup, qos2PublishMessageStore, logger)

    private val publishHandler: PublishHandler = PublishHandler(
        retainedMessageStore,
        qos1PublishMessageStore,
        qos2PublishMessageStore,
        forwardHandler,
        logger
    )

    private val pubRecHandler: PubRecHandler =
        PubRecHandler(qos2PublishMessageStore, logger)

    private val pubRelHandler: PubRelHandler =
        PubRelHandler(qos2PublishMessageStore, forwardHandler, logger)

    private val subscribeHandler: SubscribeHandler = SubscribeHandler(
        forwardHandler,
        subscriptionStore,
        retainedMessageStore,
        logger,
    )

    private val unsubscribeHandler: UnsubscribeHandler =
        UnsubscribeHandler(subscriptionStore, logger)

    private val willMessageHandler: WillMessageHandler =
        WillMessageHandler(sessionStore, forwardHandler)

    internal val mqttHandler: MqttHandler = MqttHandler(
        channelGroup,
        retryGroup,
        willMessageHandler,
        listOf(
            connectHandler,
            disconnectHandler,
            pingReqHandler,
            pubAckHandler,
            pubCompHandler,
            publishHandler,
            pubRecHandler,
            pubRelHandler,
            subscribeHandler,
            unsubscribeHandler
        ),
        sessionStore,
        builder.clientListener,
        logger
    )

    internal val exceptionCaughtHandler: ExceptionCaughtHandler = ExceptionCaughtHandler(
        willMessageHandler,
        builder.clientListener,
        sessionStore,
        logger
    )

    internal var sslContext: SslContext? = if (builder.sslEnabled) {
        SslContextBuilder.forServer(builder.serverCertFile, builder.privateKeyFile)
            .apply {
                if (builder.twoWayAuthEnabled) {
                    trustManager(builder.caCertFile)
                        .clientAuth(ClientAuth.REQUIRE)
                }
            }
            .sslProvider(SslProvider.JDK)
            .build()
    } else {
        null
    }

    @GuardedBy("stateLock")
    private var state: Int = State.IDLE

    fun start() {
        synchronized(stateLock) {
            if (state == State.RUNNING) {
                return
            }

            if (state != State.IDLE) {
                return
            }

            setState(State.STARTING)
        }

        logger.i(TAG, "MQTT Broker is starting...")

        thread(name = "Jasmine-start-thread") {
            val mqttResult = runCatching { mqttServer() }
            if (mqttResult.isFailure) {
                stopMqttServer()

                setState(State.IDLE)

                val cause = mqttResult.exceptionOrNull()!!
                logger.e(TAG, "MQTT Broker start failure on port(${builder.port})", cause)

                builder.jasmineCallback?.onStartFailure(this, cause)
                return@thread
            }

            val sslEnabled = builder.sslEnabled
            if (sslEnabled) {
                val mqttSSLResult = runCatching { mqttSSLServer() }
                if (mqttSSLResult.isFailure) {
                    stopMqttServer()
                    stopMqttSSLServer()

                    setState(State.IDLE)

                    val cause = mqttSSLResult.exceptionOrNull()!!
                    logger.e(
                        TAG,
                        "MQTT SSL Broker start failure on port(${builder.sslPort})",
                        cause
                    )

                    builder.jasmineCallback?.onStartFailure(this, cause)
                    return@thread
                }
            }

            if (!builder.wsEnabled) {
                setState(State.RUNNING)

                if (sslEnabled) {
                    logger.i(
                        TAG,
                        "MQTT Broker is running on port(${builder.port}) and sslPort(${builder.sslPort})"
                    )
                } else {
                    logger.i(TAG, "MQTT Broker is running on port(${builder.port})")
                }

                builder.jasmineCallback?.onStarted(this)
                return@thread
            }

            val websocketResult = runCatching { websocketServer() }
            if (websocketResult.isFailure) {
                stopMqttServer()
                if (sslEnabled) {
                    stopMqttSSLServer()
                }
                stopWebSocketServer()

                setState(State.IDLE)

                val cause = websocketResult.exceptionOrNull()!!
                logger.e(
                    TAG,
                    "MQTT Broker WebSocketProtocol start failure on port(${builder.wsPort})",
                    cause
                )

                builder.jasmineCallback?.onStartFailure(this, cause)
                return@thread
            }

            if (sslEnabled) {
                val websocketSSLResult = runCatching { websocketSSLServer() }
                if (websocketSSLResult.isFailure) {
                    stopMqttServer()
                    stopMqttSSLServer()
                    stopWebSocketServer()
                    stopWebSocketSSLServer()

                    setState(State.IDLE)

                    val cause = websocketSSLResult.exceptionOrNull()!!
                    logger.e(
                        TAG,
                        "MQTT SSL Broker WebSocketProtocol start failure on port(${builder.wsSSLPort})",
                        cause
                    )

                    builder.jasmineCallback?.onStartFailure(this, cause)
                    return@thread
                }
            }

            setState(State.RUNNING)

            if (sslEnabled) {
                logger.i(
                    TAG,
                    "MQTT Broker is running on port(${builder.port}) and sslPort(${builder.sslPort}), websocket on port(${builder.wsPort}) and sslPort(${builder.wsSSLPort}) and path(${builder.wsPath})"
                )
            } else {
                logger.i(
                    TAG,
                    "MQTT Broker is running on port(${builder.port}), websocket on port(${builder.wsPort}) and path(${builder.wsPath})"
                )
            }

            builder.jasmineCallback?.onStarted(this)
        }
    }

    fun stop() {
        synchronized(stateLock) {
            if (state == State.IDLE) {
                return
            }

            if (state != State.RUNNING) {
                return
            }

            setState(State.STOPPING)
        }

        logger.i(TAG, "MQTT Broker is stopping...")

        thread(name = "Jasmine-stop-thread") {
            val mqttResult = runCatching { stopMqttServer() }
            if (mqttResult.isFailure) {
                setState(State.RUNNING)

                val cause = mqttResult.exceptionOrNull()!!
                builder.jasmineCallback?.onStopFailure(this, cause)
                return@thread
            }

            val mqttSSLResult = runCatching { stopMqttSSLServer() }
            if (mqttSSLResult.isFailure) {
                setState(State.RUNNING)

                val cause = mqttSSLResult.exceptionOrNull()!!
                builder.jasmineCallback?.onStopFailure(this, cause)
                return@thread
            }

            val websocketResult = runCatching { stopWebSocketServer() }
            if (websocketResult.isFailure) {
                setState(State.RUNNING)

                val cause = websocketResult.exceptionOrNull()!!
                builder.jasmineCallback?.onStopFailure(this, cause)
                return@thread
            }

            val websocketSSLResult = runCatching { stopWebSocketSSLServer() }
            if (websocketSSLResult.isFailure) {
                setState(State.RUNNING)

                val cause = websocketSSLResult.exceptionOrNull()!!
                builder.jasmineCallback?.onStopFailure(this, cause)
                return@thread
            }

            channelGroup.close()
            retryGroup.stop()

            setState(State.IDLE)

            logger.i(TAG, "MQTT Broker is stopped")

            builder.jasmineCallback?.onStopped(this)
        }
    }

    private fun stopMqttServer() {
        mqttChannel?.close()?.sync()
        mqttChannel = null
    }

    private fun stopMqttSSLServer() {
        mqttSSLChannel?.close()?.sync()
        mqttSSLChannel = null
    }

    private fun stopWebSocketServer() {
        websocketChannel?.close()?.sync()
        websocketChannel = null
    }

    private fun stopWebSocketSSLServer() {
        websocketSSLChannel?.close()?.sync()
        websocketSSLChannel = null
    }

    fun isRunning(): Boolean = synchronized(stateLock) {
        state == State.RUNNING
    }

    fun publish(message: Message) {
        forwardHandler.handle(message)
    }

    fun publish(clientId: String, message: Message) {
        forwardHandler.handle(clientId, message)
    }

    private fun setState(@State state: Int) = synchronized(stateLock) {
        this.state = state
    }

    private fun mqttServer() {
        val bootstrap = createServerBootstrap(MqttChannelInitializer(this, false))

        val host = builder.host
        mqttChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.port)
        } else {
            bootstrap.bind(host, builder.port)
        }.sync().channel()
    }

    private fun mqttSSLServer() {
        val bootstrap = createServerBootstrap(MqttChannelInitializer(this, true))

        val host = builder.host
        mqttSSLChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.sslPort)
        } else {
            bootstrap.bind(host, builder.sslPort)
        }.sync().channel()
    }

    private fun websocketServer() {
        val bootstrap = createServerBootstrap(MqttWebSocketChannelInitializer(this, false))

        val host = builder.host
        websocketChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.wsPort)
        } else {
            bootstrap.bind(host, builder.wsPort)
        }.sync().channel()
    }

    private fun websocketSSLServer() {
        val bootstrap = createServerBootstrap(MqttWebSocketChannelInitializer(this, true))

        val host = builder.host
        websocketSSLChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.wsSSLPort)
        } else {
            bootstrap.bind(host, builder.wsSSLPort)
        }.sync().channel()
    }

    private fun createServerBootstrap(childHandler: ChannelHandler): ServerBootstrap {
        return ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 512)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(childHandler)
    }

    @Keep
    class Builder {

        internal var host: String = ""
        internal var port: Int = 1883
        internal var sslPort: Int = 1884

        internal var wsEnabled: Boolean = false
        internal var wsPort: Int = 8083
        internal var wsSSLPort: Int = 8084
        internal var wsPath: String = "/mqtt"

        internal var keepAliveTimeSeconds: Int = 5

        internal var retryIntervalMillis: Long = 3_000
        internal var maxRetries: Int = 5

        internal var sslEnabled: Boolean = false
        internal var twoWayAuthEnabled: Boolean = false
        internal var caCertFile: File? = null
        internal var serverCertFile: File? = null
        internal var privateKeyFile: File? = null

        internal var maxContentLength: Int = 65536
        internal var maxClientIdLength: Int = 255

        internal var jasmineCallback: IJasmineCallback? = null
        internal var clientListener: IClientListener? = null
        internal var authenticators = mutableListOf<IMqttAuthenticator>()

        internal var logger: Logger = DefaultLogger(true)

        fun host(host: String) = apply {
            this.host = host
        }

        fun port(port: Int) = apply {
            this.port = port
        }

        fun sslPort(port: Int) = apply {
            this.sslPort = port
        }

        fun enableWebsocket(enable: Boolean) = apply {
            this.wsEnabled = enable
        }

        fun websocketPort(port: Int) = apply {
            this.wsPort = port
        }

        fun websocketSSLPort(port: Int) = apply {
            this.wsSSLPort = port
        }

        fun websocketPath(path: String) = apply {
            this.wsPath = path
        }

        fun keepAliveInSeconds(keepAlive: Int) = apply {
            this.keepAliveTimeSeconds = keepAlive
        }

        fun retryInterval(interval: Long, unit: TimeUnit) = apply {
            this.retryIntervalMillis = unit.toMillis(interval)
        }

        fun maxRetries(max: Int) = apply {
            this.maxRetries = max
        }

        fun enableSSL(enable: Boolean) = apply {
            this.sslEnabled = enable
        }

        fun enableTwoWayAuth(enable: Boolean) = apply {
            this.twoWayAuthEnabled = enable
        }

        fun caCertFile(file: File) = apply {
            if (!(file.exists())) {
                throw IllegalArgumentException("ca cert file is not exists")
            }

            this.caCertFile = file
        }

        fun serverCertFile(file: File) = apply {
            if (!(file.exists())) {
                throw IllegalArgumentException("server cert file is not exists")
            }

            this.serverCertFile = file
        }

        fun privateKeyFile(file: File) = apply {
            if (!(file.exists())) {
                throw IllegalArgumentException("private key file is not exists")
            }

            this.privateKeyFile = file
        }

        fun maxContentLength(length: Int) = apply {
            this.maxContentLength = length
        }

        fun maxClientIdLength(length: Int) = apply {
            this.maxClientIdLength = length
        }

        fun jasmineCallback(callback: IJasmineCallback) = apply {
            this.jasmineCallback = callback
        }

        fun clientListener(listener: IClientListener) = apply {
            this.clientListener = listener
        }

        fun addAuthenticator(authenticator: IMqttAuthenticator) = apply {
            this.authenticators += authenticator
        }

        fun logger(logger: Logger) = apply {
            this.logger = logger
        }

        fun build(): Jasmine {

            if (sslEnabled) {
                if (twoWayAuthEnabled) {
                    if (caCertFile == null) {
                        throw IllegalArgumentException("Do you have inject caCertFile?")
                    }
                }

                if (serverCertFile == null) {
                    throw IllegalArgumentException("Do you have inject serverCertFile?")
                }

                if (privateKeyFile == null) {
                    throw IllegalArgumentException("Do you have inject privateKeyFile?")
                }
            }

            return Jasmine(this)
        }

        fun start(): Jasmine {
            return build().apply { start() }
        }
    }
}