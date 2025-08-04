# Jasmine

[![License: Apache 2.0](https://img.shields.io/github/license/guodongAndroid/jasmine?color=yellow)](./LICENSE.txt) ![Maven Central Version](https://img.shields.io/maven-central/v/com.sunxiaodou.android/jasmine)

Jasmine 是一个基于 Netty 并使用 Kotlin 开发的适用于 Android 的轻量级 MQTT Broker。

## 特性

- MQTT 3.1/3.1.1协议支持
- 完整的 QoS 0,1,2 消息支持
- 遗嘱消息、保留消息支持
- 订阅主题通配符支持
- WebSocket双协议支持
- 默认基于内存的消息持久化
- 必须提供用户名和密码，默认无需认证
- 支持自定义认证逻辑

## 集成

```kotlin
implementation("com.sunxiaodou.android:jasmine:0.0.2")
```

## 使用

### 基本使用

```kotlin
val jasmine = Jasmine.Builder().start() // 默认端口1883
```

启动成功后，使用任意MQTT 客户端访问 `tcp://{IP}:1883` 连接 Broker。

### 进阶使用

```kotlin
val jasmine = Jasmine.Builder()
	.port(18883) // 指定端口
	.enableWebsocket(true) // 启用WebSocket
	.websocketPort(8083) // 指定WebSocket端口
	.websocketPath("/mqtt") // 指定WebSocket路径
	.keepAliveInSeconds(5) // 指定客户端保持时长，单位秒，默认5s客户端与Broker没有交互即断开连接
	.retryInterval(3_000) // 指定QoS1/QoS2消息重发间隔时长，单位毫秒，默认3_000毫秒
	.maxRetries(5) // 指定QoS1/QoS2消息最大重发次数，默认5次
	.maxContentLength(65536) // 指定单个消息的最大内容长度，默认65536字节
	.maxClientIdLength(255) // 指定客户端ClientId的最大长度，默认255
	.jasmineCallback(this) // 注入Jasmine回调
	.clientListener(this) // 注入客户端监听器
	.addAuthenticator(this) // 添加自定义认证器
	.start()
```

## 后续功能

- SSL连接
- 数据转发