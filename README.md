# Jasmine

Jasmine 是一个使用 Kotlin 且基于 Netty 开发的适用于 Android 的轻量级 MQTT Broker。

## 特性

- MQTT 3.1/3.1.1协议支持
- 完整的 QoS 0,1,2 消息支持
- 遗嘱消息、保留消息支持
- 订阅主题通配符支持
- WebSocket双协议支持
- 默认基于内存的消息持久化
- 必须提供用户名和密码，默认无需认证
- 提供认证拦截器实现自定义认证逻辑

## 使用

```kotlin
val jasmine = Jasmine.Builder().start()
```

## 后续功能

- SSL连接
- 数据转发