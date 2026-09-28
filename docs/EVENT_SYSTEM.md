# 事件系统

## 背景

早期版本的事件派发是**纯同步**的：`EventManager.fire(event)` 依次反射调用所有处理器，返回时事件必定已定稿。

这让"需要异步决策"的处理器无处安放。典型的处理方式是调用方自己挂起流程：

```java
// 旧写法：调用方手工编排"等事件真正结束"
preLoginEvent.registerIntent("my-plugin");
someAsyncLookup().whenComplete((result, error) -> {
    preLoginEvent.forceOnlineMode();
    preLoginEvent.completeIntent("my-plugin");
});
```

问题在于"事件何时结束"这件事被切成了两半：框架负责调用处理器，调用方负责猜什么时候真的结束了。由此产生了两个后果：

1. **异步设置的状态会被静默忽略** —— 调用方在 intent 完成之前就读取了事件。
2. **每个需要异步的事件都要重抄一遍 intent 机制** —— 它属于单个事件，而非框架。

现在改为**可暂停的派发**：处理器能声明"我还没做完"，框架负责在它完成之后从下一个处理器继续。异步能力收归框架，调用方只需等待 `fire()` 返回的 future。

---

## 核心契约

```
所有处理器按优先级串行执行
任一处理器都可以暂停派发，并在任意线程恢复
暂停不阻塞任何线程
fire() 返回的 future 完成时，事件必定已定稿
```

最后一条是调用方唯一需要理解的东西：**future 完成 = 可以安全读取事件**。

---

## 处理器写法

### 1. 同步处理（最常用）

```java
@EventHandler
public void onPlayerLogin(PlayerLoginEvent event) {
    // 直接读完写完
}
```

方法返回时处理完毕，框架立即调用下一个处理器。

### 2. 需要异步结果

```java
@EventHandler
public EventTask onGameProfileRequest(GameProfileRequestEvent event, Continuation continuation) {
    skinLookup(event.getProfile().uuid()).whenComplete((skin, error) -> {
        if (error != null) {
            continuation.resumeWithException(error);
        } else {
            event.setGameProfile(withSkin(skin));
            continuation.resume();
        }
    });
    return EventTask.withContinuation(c -> {});
}
```

更简洁的等价写法，适合"等待一个 `CompletableFuture`"：

```java
@EventHandler
public EventTask onGameProfileRequest(GameProfileRequestEvent event) {
    CompletableFuture<SkinData> lookup = skinLookup(event.getProfile().uuid());
    lookup.thenAccept(event::setGameProfile);
    return EventTask.resumeWhenComplete(lookup);
}
```

### 3. 阻塞式处理

```java
@EventHandler(async = true)
public void onProxyPing(ProxyPingEvent event) {
    event.setDescription(blockingDatabaseQuery());   // 会阻塞，但不在调用方线程上
}
```

`async = true` 表示该处理器必须换线程执行。**只要某个事件存在这样的处理器，整个事件的派发都会在事件执行器（虚拟线程）上开始**，以免阻塞调用方。

### 4. 只想在事件完成后做点事

```java
@EventHandler
public EventTask onPlayerLogin(PlayerLoginEvent event) {
    return EventTask.of(() -> auditLog.write(event.getPlayer()));
}
```

`EventTask.of(task)` 执行 `task` 后恢复。**任务抛异常也会恢复**（以 `resumeWithException` 上报），不会让派发卡死。

---

## 支持的处理器签名

注册时解析一次并固化，派发期不做反射判断：

| 签名 | 可否暂停 | 说明 |
|---|---|---|
| `void m(E)` | 否 | 同步处理 |
| `void m(E, Continuation)` | 否 | **只能同步恢复**；返回前不恢复就是处理器自身的问题 |
| `EventTask m(E)` | 是 | 由返回值决定是否暂停 |
| `EventTask m(E, Continuation)` | 是 | 同上，并可直接使用该凭据 |

签名不合法（参数数量/类型、返回类型不是 `void` 或 `EventTask`）会在注册时记录警告并跳过该处理器。

---

## `EventTask` 工厂方法

| 方法 | 语义 |
|---|---|
| `completed()` | 立即完成，不暂停 |
| `of(Runnable)` | 执行后恢复；抛异常也会恢复 |
| `async(Runnable)` | 在事件执行器上执行后恢复；`requiresAsync()` 为 `true` |
| `withContinuation(Consumer<Continuation>)` | 由处理器自行决定何时恢复 |
| `resumeWhenComplete(CompletableFuture<?>)` | 等待给定 future 完成后恢复 |

> **`completed()` 与 `withContinuation(c -> {})` 完全不同。**
> 前者立即恢复，后者表示"我会自己恢复"。用错会导致派发永久挂起。

---

## 派发入口

| 方法 | 用途 |
|---|---|
| `fire(event)` → `CompletableFuture<E>` | 通用入口，返回"事件定稿"信号 |
| `fireAndForget(event)` | 纯通知，不关心完成时机 |
| `fireSync(event)` → `E` | 同步 API 专用；处理器若暂停会**立即抛异常** |

`fire()` 的两种用法：

```java
// 串接后续动作（推荐，尤其在有挂起可能时）
eventManager.fire(event).thenRun(() -> continueAfterwards());

// 同步等待（仅在确定不会挂起、或不在事件循环上时使用）
eventManager.fire(event).join();
```

---

## 线程模型

```
fire() 在调用方线程开始派发
   │
   ├─ 处理器（同步）───────────────► 在调用方线程执行
   ├─ 处理器（返回 EventTask）
   │      ├─ 返回前已恢复 ─────────► 调用方线程继续循环（无任何线程调度）
   │      └─ 返回后才恢复 ─────────► 恢复线程继续循环
   └─ 处理器（async = true）──────► 提交到事件执行器（虚拟线程）

事件执行器 = ProxyExecutors.getEventExecutor()
             每任务一个虚拟线程，插件之间不会互相饿死
```

**两次线程归属的判定依据是"恢复发生在处理器返回之前还是之后"，与恢复在哪个线程无关。**

写处理器时不要假定自己运行在固定线程上。

---

## 必须注意的事项

### ⚠️ 不要在 Netty 事件循环上阻塞等待 future

```java
// ✗ 错误：工作线程只有 4 个，任意几个连接同时等待就会让整个代理停摆
proxy.getEventManager().fire(handshakeEvent).join();
continueHandling(handshakeEvent);

// ✓ 正确：把后续逻辑放进回调，并投回事件循环以保持 channel 线程亲和性
proxy.getEventManager().fire(handshakeEvent).thenRun(() ->
        ctx.channel().eventLoop().execute(() -> continueHandling(handshakeEvent)));
```

这个错误的表现是"代理完全没响应"——连 ping 都不通，而不只是某个功能坏掉。

### ⚠️ 依赖事件结果的**状态更新**必须同步完成

`fire()` 返回 future 之后事件才会定稿。如果某个状态被后续代码读取（例如交给解码器的连接状态），**必须在 `fire()` 之前同步设置**：

```java
// ✗ 错误：该状态被下一个数据包的解码器读取，推迟设置会让它读到旧值
fire(event).thenRun(() -> context.setInboundState(LOGIN));

// ✓ 正确：不依赖事件的部分同步做完，只把依赖事件结果的部分放进回调
context.setInboundState(LOGIN);          // 来自客户端包字段，不依赖事件
fire(event).thenRun(() -> applyOverride(event));
```

判断标准：**这个值会不会在事件定稿之前被别处读走？**

### ⚠️ 返回 `EventTask` 就必须确保恢复

```java
// ✗ 错误：用了 withContinuation 却忘了 resume，派发永久挂起
return EventTask.withContinuation(c -> { doAsyncWork(); });

// ✓ 正确
return EventTask.withContinuation(c -> doAsyncWork().whenComplete((r, e) -> c.resume()));
```

若无法保证，改用 `EventTask.of(...)` / `resumeWhenComplete(...)`，它们在异常时也会恢复。

### ⚠️ 只能恢复一次

重复 `resume()` 抛 `IllegalStateException`。这个限制是有意的——它能暴露"两条路径都试图恢复"的逻辑错误。

### ⚠️ 取消会终止传播

事件被取消后，派发立即停止，后续处理器不再执行。需要观察已取消事件的处理器必须显式声明：

```java
@EventHandler(priority = EventPriority.MONITOR, acceptsCancelled = true)
public void observe(CancellableEvent event) { /* 只读 */ }
```

### ⚠️ `fireSync` 遇到可暂停的处理器会抛异常

`fireSync` 用于无法改为异步的同步 API（例如返回 `boolean` 的权限检查）。它**不允许**处理器暂停：

```
IllegalStateException: Event handler suspended the dispatch of XxxEvent,
which cannot be awaited synchronously. Use EventManager#fire instead.
```

看到这个异常说明"某个处理器需要暂停，但调用点无法等待"——要么改调用点，要么改处理器。

---

## 与旧版的行为差异

| 方面 | 旧版 | 现版 |
|---|---|---|
| `fire()` 返回值 | 事件实例 | `CompletableFuture<事件实例>` |
| 异步支持 | 仅 `PreLoginEvent`（intent 机制） | 所有事件 |
| 处理器声明异步 | `event.registerIntent(id)` | 返回 `EventTask` |
| `fireAsync()` | 存在（语义上只是"换个线程跑同步循环"） | **已移除**；线程由处理器的 `async` 决定 |
| 取消 | 仅跳过 `ignoreCancelled = true` 的处理器，循环继续 | **终止传播** |
| 注解参数 | `ignoreCancelled` | `acceptsCancelled`（语义相反） |
| `PreLoginEvent.registerIntent/completeIntent` | 存在 | **已移除**，改用 `EventTask` |

### 复杂度确实提升了

简单处理器**完全不受影响**：

```java
@EventHandler
public void onX(XEvent event) { ... }
```

额外的复杂度只在需要异步时出现。`EventTask` 的五个工厂方法覆盖了绝大多数场景，`withContinuation` 只在需要精细控制时才用。

**但代价是真实的**：调用方从"读返回值"变成"等 future"，任何"在事件之后更新、却被别处读取"的状态都会静默错位。这是迁移时最容易踩的坑。

---

## 迁移对照

```java
// 旧：同步等待事件
proxy.getEventManager().fire(event);
if (event.isCancelled()) { ... }

// 新：等待定稿（确定不会挂起时）
proxy.getEventManager().fire(event).join();
if (event.isCancelled()) { ... }

// 新：等待定稿（可能挂起时，必须链式）
proxy.getEventManager().fire(event).thenRun(() -> {
    if (event.isCancelled()) { ... }
});
```

```java
// 旧：intent 机制
event.registerIntent("my-plugin");
asyncWork().whenComplete((r, e) -> {
    event.setSomething(r);
    event.completeIntent("my-plugin");
});

// 新：返回 EventTask
@EventHandler
public EventTask onX(XEvent event, Continuation continuation) {
    asyncWork().whenComplete((r, e) -> {
        event.setSomething(r);
        continuation.resume();
    });
    return EventTask.withContinuation(c -> {});
}
```

```java
// 旧：fireAsync
proxy.getEventManager().fireAsync(event).thenRun(...);

// 新：fire 返回 future；需要异步执行则由处理器的 async 声明
proxy.getEventManager().fire(event).thenRun(...);
```
