package com.guodong.android.jasmine

import androidx.annotation.GuardedBy
import androidx.annotation.IntDef
import androidx.annotation.Keep
import com.guodong.android.jasmine.common.HTTP_AGGREGATOR_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.HTTP_CODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.HTTP_COMPRESSOR_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.IDLE_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_BROKER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_DECODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_ENCODER_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.WEBSOCKET_MQTT_CHANNEL_HANDLER
import com.guodong.android.jasmine.common.WEBSOCKET_PROTOCOL_CHANNEL_HANDLER
import com.guodong.android.jasmine.core.auth.IMqttAuthenticator
import com.guodong.android.jasmine.core.channel.ChannelGroup
import com.guodong.android.jasmine.core.channel.DefaultChannelGroup
import com.guodong.android.jasmine.core.channel.ExceptionCaughtHandler
import com.guodong.android.jasmine.core.channel.MqttHandler
import com.guodong.android.jasmine.core.codec.MqttWebSocketCodec
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
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.mqtt.MqttDecoder
import io.netty.handler.codec.mqtt.MqttEncoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.timeout.IdleStateHandler
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
    private var websocketChannel: Channel? = null

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

    private val mqttHandler: MqttHandler = MqttHandler(
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

    private val exceptionCaughtHandler: ExceptionCaughtHandler = ExceptionCaughtHandler(
        willMessageHandler,
        builder.clientListener,
        sessionStore,
        logger
    )

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

            if (!builder.wsEnabled) {
                setState(State.RUNNING)

                logger.i(TAG, "MQTT Broker is running on port(${builder.port})")
                builder.jasmineCallback?.onStarted(this)
                return@thread
            }

            val websocketResult = runCatching { websocketServer() }
            if (websocketResult.isFailure) {
                stopMqttServer()
                stopWebSocketServer()

                setState(State.IDLE)

                val cause = mqttResult.exceptionOrNull()!!
                logger.e(
                    TAG,
                    "MQTT Broker WebSocketProtocol start failure on port(${builder.wsPort})",
                    cause
                )

                builder.jasmineCallback?.onStartFailure(this, cause)
                return@thread
            }

            setState(State.RUNNING)

            logger.i(
                TAG,
                "MQTT Broker is running on port(${builder.port}), websocket on port(${builder.wsPort}) and path(${builder.wsPath})"
            )
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

            val websocketResult = runCatching { stopWebSocketServer() }
            if (websocketResult.isFailure) {
                setState(State.RUNNING)

                val cause = mqttResult.exceptionOrNull()!!
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

    private fun stopWebSocketServer() {
        websocketChannel?.close()?.sync()
        websocketChannel = null
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
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 512)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addFirst(
                            IDLE_CHANNEL_HANDLER,
                            IdleStateHandler(
                                0L,
                                0L,
                                builder.keepAliveTimeSeconds.toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                        .addLast(
                            MQTT_DECODER_CHANNEL_HANDLER,
                            MqttDecoder(builder.maxContentLength, builder.maxClientIdLength)
                        )
                        .addLast(MQTT_ENCODER_CHANNEL_HANDLER, MqttEncoder.INSTANCE)
                        .addLast(MQTT_BROKER_CHANNEL_HANDLER, mqttHandler)
                        .addLast(MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER, exceptionCaughtHandler)
                }
            })

        val host = builder.host
        mqttChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.port)
        } else {
            bootstrap.bind(host, builder.port)
        }.sync().channel()
    }

    private fun websocketServer() {
        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 512)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .handler(LoggingHandler(LogLevel.INFO))
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline()
                        .addFirst(
                            IDLE_CHANNEL_HANDLER,
                            IdleStateHandler(
                                0L,
                                0L,
                                builder.keepAliveTimeSeconds.toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                        .addLast(HTTP_CODER_CHANNEL_HANDLER, HttpServerCodec())
                        .addLast(
                            HTTP_AGGREGATOR_CHANNEL_HANDLER,
                            HttpObjectAggregator(builder.maxContentLength)
                        )
                        .addLast(HTTP_COMPRESSOR_CHANNEL_HANDLER, HttpContentCompressor())
                        .addLast(
                            WEBSOCKET_PROTOCOL_CHANNEL_HANDLER,
                            WebSocketServerProtocolHandler(
                                builder.wsPath,
                                "mqtt,mqttv3.1,mqttv3.1.1",
                                true,
                                builder.maxContentLength,
                            )
                        )
                        .addLast(WEBSOCKET_MQTT_CHANNEL_HANDLER, MqttWebSocketCodec)
                        .addLast(MQTT_DECODER_CHANNEL_HANDLER, MqttDecoder())
                        .addLast(MQTT_ENCODER_CHANNEL_HANDLER, MqttEncoder.INSTANCE)
                        .addLast(MQTT_BROKER_CHANNEL_HANDLER, mqttHandler)
                        .addLast(MQTT_EXCEPTION_CAUGHT_CHANNEL_HANDLER, exceptionCaughtHandler)
                }
            })

        val host = builder.host
        websocketChannel = if (host.isEmpty() || host.isBlank()) {
            bootstrap.bind(builder.wsPort)
        } else {
            bootstrap.bind(host, builder.wsPort)
        }.sync().channel()
    }

    @Keep
    class Builder {

        internal var host: String = ""
        internal var port: Int = 1883

        internal var wsEnabled: Boolean = false
        internal var wsPort: Int = 8083
        internal var wsPath: String = "/mqtt"

        internal var keepAliveTimeSeconds: Int = 5

        internal var retryIntervalMillis: Long = 3_000
        internal var maxRetries: Int = 5

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

        fun enableWebsocket(enable: Boolean) = apply {
            this.wsEnabled = enable
        }

        fun websocketPort(port: Int) = apply {
            this.wsPort = port
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
            return Jasmine(this)
        }

        fun start(): Jasmine {
            return Jasmine(this).apply { start() }
        }
    }
}