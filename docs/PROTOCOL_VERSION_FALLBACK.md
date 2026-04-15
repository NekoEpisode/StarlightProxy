# Protocol Version Fallback 机制

## 背景

Minecraft 协议版本号（Protocol Version）是一个正整数，由客户端在 Handshake 包中携带。
代理在 `PacketRegistry` 里以协议版本号为 key 存储包工厂，不同版本可以注册不同的包实现。

然而有些包的格式在所有版本中完全相同（例如握手包、Status 请求/响应包），
为这些包在每个版本下各注册一次既繁琐又没有意义。
为此引入了 **`ALL_VERSION`** 占位符和对应的 **fallback 查找策略**。

---

## ALL_VERSION 占位符

```java
// ProtocolVersion.java
ALL_VERSION(-1)
```

- 协议版本号为 **`-1`**
- Mojang 官方协议版本号均为正整数，`-1` 不会与任何真实版本冲突
- 语义：**"该包适用于所有协议版本，与版本无关"**

---

## 查找优先级（PacketRegistry.createPacket / RegistryPacketUtils）

```
1. 先按「具体版本」查找（例如 775）
        ↓ 找不到
2. 再按「ALL_VERSION（-1）」查找
        ↓ 还找不到
3. 抛出 IllegalArgumentException
```

> **注意**：`UNKNOWN`（-2）与任何其他未注册的版本号行为完全相同 ——
> 先查 -2，找不到后自动 fallback 到 ALL_VERSION（-1）。
> 因此无需为 UNKNOWN 特殊处理。

具体实现（`PacketRegistry` 与 `RegistryPacketUtils` 均遵循此逻辑）：

```java
// 1. 精确匹配
result = lookup(protocolVersion, ...);
// 2. Fallback
if (result == null && protocolVersion != ALL_VERSION) {
    result = lookup(ALL_VERSION, ...);
}
```

`hasPacket` 同样遵循此策略。

---

## 哪些包应该注册在 ALL_VERSION 下

| 包 / 阶段 | 理由 |
|---|---|
| **Handshake** `ServerboundHandshakePacket` | 握手阶段协议版本尚未确定，且握手包格式从未变过 |
| **Status** 请求 / 响应 / Ping | 格式极稳定，几乎不随版本变化 |
| 自定义插件频道内部通信包 | 完全由代理自身定义，与 Mojang 版本无关 |

> **不建议**将 Login / Configuration / Play 阶段的包注册到 `ALL_VERSION`，
> 这些阶段的包格式随版本迭代经常发生变化，应为每个版本单独注册。

---

## 示例：注册一个 ALL_VERSION 包

```java
// 适用于所有版本的 Status 响应包
packetRegistry.registerPacket(
    ProtocolVersion.ALL_VERSION.getProtocolVersionCode(), // -1
    ProtocolState.STATUS,
    ProtocolDirection.CLIENTBOUND,
    0x00,
    ClientboundStatusResponsePacket::new
);
```

当客户端以版本 `775` 连接并进入 STATUS 阶段时，
`createPacket(775, STATUS, CLIENTBOUND, 0x00)` 会先查版本 `775`，
未命中后自动 fallback 到 `-1`，成功找到上面注册的工厂。

---

## 性能影响

Fallback 最多增加 **4 次 `ConcurrentHashMap.get()` 调用**（各层级各一次），
均为 O(1) 操作，纳秒级，相比网络 I/O 和包解析开销完全可忽略不计。

