package io.slidermc.starlight.api.event;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.plugin.IPlugin;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 事件总线，负责监听器的注册、注销与事件派发。
 *
 * <p>监听器通过 {@link io.slidermc.starlight.api.event.EventListener} 接口 + {@link EventHandler} 注解的方式声明。
 * 注册时需要提供一个 {@code listenerId}，该 ID 在同一来源（插件或内核）内应唯一；
 * 内核注册无需关联插件，插件注册需传入 {@link IPlugin} 以支持按插件批量注销。
 *
 * <p>用法示例（插件内）：
 * <pre>{@code
 * // 注册
 * proxy.getEventManager().register(this, "main-listener", new MyListener());
 *
 * // 注销单个
 * proxy.getEventManager().unregister(this, "main-listener");
 *
 * // 注销插件所有监听器（onDisable 中调用）
 * proxy.getEventManager().unregisterAll(this);
 * }</pre>
 *
 * <p>用法示例（内核内）：
 * <pre>{@code
 * eventManager.register("login-handler", new LoginListener());
 * }</pre>
 */
public class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    /** 内核注册使用的虚拟来源标识，与任何插件 ID 不冲突。 */
    private static final String KERNEL_SOURCE = "__starlight_kernel__";

    /**
     * 保护 {@link #handlerMap}、{@link #listenerIndex}、{@link #pluginListenerKeys} 复合操作的锁。
     * 注册、注销、批量注销均需持有此锁，以保证 check-then-act 和多集合一致性。
     */
    private final ReentrantLock registryLock = new ReentrantLock();

    /**
     * 已注册处理器的内部记录，按事件类型分组，每组按优先级降序排列。
     * key: 事件类型 Class，value: 该类型下所有已注册的处理器（已排序）
     */
    private final Map<Class<? extends IStarlightEvent>, List<Invocation>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 来源ID（{@code "pluginId::listenerId"} 或 {@code "__starlight_kernel__::listenerId"}）
     * 到该监听器产生的所有 {@link Invocation} 的映射，用于快速注销。
     */
    private final Map<String, List<Invocation>> listenerIndex = new ConcurrentHashMap<>();

    /**
     * 插件 ID 到该插件所有监听器来源 key 的映射，用于批量注销。
     */
    private final Map<String, Set<String>> pluginListenerKeys = new ConcurrentHashMap<>();

    /**
     * 事件执行器。
     *
     * <p>用于运行声明了 {@link EventHandler#async()} 的处理器。Starlight 传入的是虚拟线程执行器，
     * 因此单个处理器的阻塞不会占用平台线程。
     */
    private final Executor eventExecutor;
    private final TranslateManager translateManager;

    /**
     * 构造一个使用公共 ForkJoinPool 作为事件执行器的 EventManager。
     */
    public EventManager(TranslateManager translateManager) {
        this(ForkJoinPool.commonPool(), translateManager);
    }

    /**
     * 构造一个使用指定 Executor 作为事件执行器的 EventManager。
     *
     * @param eventExecutor  用于执行声明了 {@link EventHandler#async()} 的处理器的 Executor
     * @param translateManager 翻译管理器
     */
    public EventManager(Executor eventExecutor, TranslateManager translateManager) {
        this.eventExecutor = Objects.requireNonNull(eventExecutor, "eventExecutor");
        this.translateManager = Objects.requireNonNull(translateManager, "translateManager");
    }

    /**
     * 返回事件执行器，用于运行声明了 {@link EventHandler#async()} 的处理器。
     *
     * @return 事件执行器
     */
    Executor eventExecutor() {
        return eventExecutor;
    }

    /**
     * 返回翻译管理器。
     *
     * <p>包内可见：派发过程中的非 debug 日志同样需要走翻译，而 {@link EventDispatch}
     * 与本类的内部 record 都无法直接访问本字段。
     *
     * @return 翻译管理器
     */
    TranslateManager translateManager() {
        return translateManager;
    }

    /**
     * 以内核身份注册监听器。
     *
     * <p>内核注册的监听器不与任何插件关联，无法通过 {@link #unregisterAll(IPlugin)} 注销，
     * 只能通过 {@link #unregister(String)} 单独注销。
     *
     * @param listenerId 监听器唯一 ID，同一内核来源下不可重复
     * @param listener   监听器实例
     * @throws IllegalArgumentException 若该 ID 已被注册
     */
    public void register(String listenerId, io.slidermc.starlight.api.event.EventListener listener) {
        registryLock.lock();
        try {
            registerInternal(KERNEL_SOURCE, listenerId, listener);
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * 以插件身份注册监听器。
     *
     * <p>同一插件内 {@code listenerId} 不可重复。可通过 {@link #unregisterAll(IPlugin)} 注销该插件的所有监听器。
     *
     * @param plugin     注册来源插件
     * @param listenerId 监听器唯一 ID，同一插件内不可重复
     * @param listener   监听器实例
     * @throws IllegalArgumentException 若该插件下该 ID 已被注册
     */
    public void register(IPlugin plugin, String listenerId, io.slidermc.starlight.api.event.EventListener listener) {
        String pluginId = plugin.getDescription().name();
        registryLock.lock();
        try {
            registerInternal(pluginId, listenerId, listener);
            pluginListenerKeys.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet())
                    .add(compositeKey(pluginId, listenerId));
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * 注销内核注册的指定 ID 的监听器。
     *
     * @param listenerId 监听器 ID
     */
    public void unregister(String listenerId) {
        registryLock.lock();
        try {
            unregisterInternal(KERNEL_SOURCE, listenerId);
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * 注销插件注册的指定 ID 的监听器。
     *
     * @param plugin     注册来源插件
     * @param listenerId 监听器 ID
     */
    public void unregister(IPlugin plugin, String listenerId) {
        String pluginId = plugin.getDescription().name();
        registryLock.lock();
        try {
            unregisterInternal(pluginId, listenerId);
            Set<String> keys = pluginListenerKeys.get(pluginId);
            if (keys != null) {
                keys.remove(compositeKey(pluginId, listenerId));
            }
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * 注销指定插件注册的所有监听器。
     * 通常在插件 {@code onDisable()} 阶段由 {@link io.slidermc.starlight.plugin.PluginManager} 自动调用。
     *
     * @param plugin 插件实例
     */
    public void unregisterAll(IPlugin plugin) {
        String pluginId = plugin.getDescription().name();
        String pluginIdForLog;

        registryLock.lock();
        try {
            Set<String> keys = pluginListenerKeys.remove(pluginId);
            if (keys == null) return;
            for (String key : keys) {
                List<Invocation> handlers = listenerIndex.remove(key);
                if (handlers == null) continue;
                for (Invocation handler : handlers) {
                    List<Invocation> list = handlerMap.get(handler.eventType());
                    if (list != null) {
                        list.remove(handler);
                    }
                }
            }
            pluginIdForLog = pluginId;
        } finally {
            registryLock.unlock();
        }

        if (pluginIdForLog != null && log.isDebugEnabled()) {
            log.debug("已注销插件 [{}] 的所有事件监听器", pluginIdForLog);
        }
    }

    /**
     * 派发一个事件，按优先级从高到低依次调用所有已注册的处理器。
     *
     * <p>默认情况下（{@code polymorphic = false}）仅精确匹配事件类型；若处理器声明了
     * {@code polymorphic = true}，则采用继承关系匹配，事件类型为其参数类型的子类时同样会被触发。
     *
     * <p>处理器可以暂停派发：返回带有 {@link Continuation} 的 {@link EventTask} 后，派发会在
     * 该处理器处暂停，直到它恢复。暂停不阻塞任何线程，且不影响其余处理器的执行顺序。
     *
     * <p>返回的 future 在<b>所有</b>处理器（含异步恢复的）都完成后完成，携带传入的事件实例。
     * 需要同步结果时调用 {@link CompletableFuture#join()}；需要串接后续动作时使用
     * {@link CompletableFuture#thenAccept(Consumer)}。
     *
     * <p>若事件实现了 {@link ICancellableEvent} 并在派发过程中被取消，派发会立即终止，
     * 后续处理器不再被调用。
     *
     * <p><b>不要在 Netty 事件循环线程上阻塞等待返回的 future。</b>处理器可以暂停派发，
     * 而工作线程数量有限（见 {@code NioEventLoopGroup} 的构造），若干个连接同时等待就会让
     * 整个代理停摆。事件循环上的调用方应改用链式续跑：
     * <pre>{@code
     * eventManager.fire(event).thenRun(() ->
     *         ctx.channel().eventLoop().execute(() -> continueHandling(event)));
     * }</pre>
     * 若处理逻辑无法改为异步（例如同步签名的 API），应改用
     * {@link #fireSync(IStarlightEvent)}，它会在处理器试图暂停时立即失败而不是永久阻塞。
     *
     * @param event 要派发的事件
     * @param <E>   事件类型
     * @return 在事件定稿后完成的 future，携带传入的事件实例
     */
    public <E extends IStarlightEvent> CompletableFuture<E> fire(E event) {
        CompletableFuture<E> future = new CompletableFuture<>();
        EventDispatch.dispatch(this, event, collectHandlers(event), future);
        return future;
    }

    /**
     * 同步派发一个事件，直接返回事件实例。
     *
     * <p>用于无法改为异步的调用点（例如返回 {@code boolean} 的同步 API）：若某个处理器试图
     * 暂停派发，本方法会立即抛出 {@link IllegalStateException}，而不是让调用方永久阻塞。
     *
     * <p>仅在<b>确定没有处理器会暂停</b>时使用，否则应改用 {@link #fire(IStarlightEvent)}
     * 的链式形式。
     *
     * @param event 要派发的事件
     * @param <E>   事件类型
     * @return 传入的事件实例
     * @throws IllegalStateException 若有处理器暂停了派发
     */
    public <E extends IStarlightEvent> E fireSync(E event) {
        EventDispatch.dispatchSync(this, event, collectHandlers(event));
        return event;
    }

    /**
     * 派发一个事件但不关心完成时机。
     *
     * <p>语义与 {@link #fire(IStarlightEvent)} 相同，只是不产生完成信号。适用于纯通知型事件。
     *
     * @param event 要派发的事件
     */
    public void fireAndForget(IStarlightEvent event) {
        EventDispatch.dispatch(this, event, collectHandlers(event), null);
    }

    /**
     * 收集该事件命中的处理器，按优先级降序排列。
     *
     * @param event 事件实例
     * @return 命中的处理器；无命中时返回 {@code null}
     */
    private List<Invocation> collectHandlers(IStarlightEvent event) {
        Class<? extends IStarlightEvent> exactType = event.getClass();
        List<Invocation> matches = new ArrayList<>();

        for (Map.Entry<Class<? extends IStarlightEvent>, List<Invocation>> entry : handlerMap.entrySet()) {
            boolean isExact = entry.getKey() == exactType;
            for (Invocation handler : entry.getValue()) {
                // 精确匹配的处理器始终包含；多态处理器额外检查继承关系
                if (isExact || (handler.polymorphic() && entry.getKey().isAssignableFrom(exactType))) {
                    matches.add(handler);
                }
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        matches.sort(Comparator.comparingInt(h -> -h.priority().getOrder()));
        return List.copyOf(matches);
    }

    /**
     * 返回指定事件类型当前已注册的处理器数量，用于调试与测试。
     *
     * @param eventType 事件类型
     * @return 处理器数量
     */
    public int getHandlerCount(Class<? extends IStarlightEvent> eventType) {
        List<Invocation> list = handlerMap.get(eventType);
        return list == null ? 0 : list.size();
    }

    private void registerInternal(String sourceId, String listenerId, io.slidermc.starlight.api.event.EventListener listener) {
        String key = compositeKey(sourceId, listenerId);
        List<Invocation> existing = listenerIndex.putIfAbsent(key, List.of());
        if (existing != null) {
            String pattern = translateManager.translate("starlight.logging.error.event.listener_id_duplicate");
            throw new IllegalArgumentException(formatTranslated(pattern, listenerId, sourceId));
        }

        List<Invocation> discovered = new ArrayList<>();
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;

            Invocation invocation = resolveInvocation(sourceId, listenerId, listener, method, annotation);
            if (invocation != null) {
                discovered.add(invocation);
            }
        }

        if (discovered.isEmpty()) {
            listenerIndex.remove(key);
            log.warn(translateManager.translate("starlight.logging.warn.event.no_valid_handlers"),
                    listener.getClass().getName(), sourceId);
            return;
        }

        listenerIndex.put(key, discovered);
        for (Invocation handler : discovered) {
            List<Invocation> newList = handlerMap.computeIfAbsent(handler.eventType(), k -> new CopyOnWriteArrayList<>());
            newList.add(handler);
            replaceWithSortedCopy(handler.eventType(), newList);
        }
        log.debug("已注册监听器 [{}] 来自 [{}]，包含 {} 个处理器",
                listenerId, sourceId, discovered.size());
    }

    /**
     * 校验一个被 {@link EventHandler} 标注的方法，并解析为可派发的 {@link Invocation}。
     *
     * @param sourceId   来源标识
     * @param listenerId 监听器 ID
     * @param listener   监听器实例
     * @param method     被标注的方法
     * @param annotation 方法上的注解
     * @return 解析结果；方法签名不合法时返回 {@code null} 并记录警告
     */
    private Invocation resolveInvocation(
            String sourceId,
            String listenerId,
            io.slidermc.starlight.api.event.EventListener listener,
            Method method,
            EventHandler annotation
    ) {
        Class<?>[] parameters = method.getParameterTypes();
        boolean wantsContinuation = parameters.length == 2
                && Continuation.class.isAssignableFrom(parameters[1]);

        if (parameters.length < 1 || parameters.length > 2 || (parameters.length == 2 && !wantsContinuation)) {
            log.warn(translateManager.translate("starlight.logging.warn.event.handler_param_count_invalid"),
                    listener.getClass().getName(), method.getName());
            return null;
        }

        if (!IStarlightEvent.class.isAssignableFrom(parameters[0])) {
            log.warn(translateManager.translate("starlight.logging.warn.event.handler_param_type_invalid"),
                    listener.getClass().getName(), method.getName(), parameters[0].getName());
            return null;
        }

        Class<?> returnType = method.getReturnType();
        boolean returnsTask = EventTask.class.isAssignableFrom(returnType);
        if (!returnsTask && returnType != void.class) {
            log.warn(translateManager.translate("starlight.logging.warn.event.handler_return_type_invalid"),
                    listener.getClass().getName(), method.getName(), returnType.getName());
            return null;
        }

        @SuppressWarnings("unchecked")
        Class<? extends IStarlightEvent> eventType = (Class<? extends IStarlightEvent>) parameters[0];
        method.setAccessible(true);

        // 声明了 async 或返回需要换线程的任务，都视为必须异步执行
        boolean requiresAsync = annotation.async();

        return new Invocation(
                sourceId, listenerId, listener, method, eventType,
                annotation.priority(), annotation.acceptsCancelled(), annotation.polymorphic(),
                wantsContinuation, returnsTask, requiresAsync
        );
    }

    private void unregisterInternal(String sourceId, String listenerId) {
        String key = compositeKey(sourceId, listenerId);
        List<Invocation> handlers = listenerIndex.remove(key);
        if (handlers == null) {
            log.warn(translateManager.translate("starlight.logging.warn.event.unregister_nonexistent"), listenerId, sourceId);
            return;
        }
        for (Invocation handler : handlers) {
            List<Invocation> list = handlerMap.get(handler.eventType());
            if (list != null) {
                list.remove(handler);
            }
        }
        log.debug("已注销监听器 [{}] (来源: {})", listenerId, sourceId);
    }

    /**
     * 将指定事件类型的处理器列表替换为按优先级降序排列的新 {@link CopyOnWriteArrayList}。
     * 避免 COW 列表的 {@code sort()} 非线程安全问题。
     */
    private void replaceWithSortedCopy(Class<? extends IStarlightEvent> eventType, List<Invocation> currentList) {
        List<Invocation> sorted = new ArrayList<>(currentList);
        sorted.sort(Comparator.comparingInt(h -> -h.priority().getOrder()));
        handlerMap.put(eventType, new CopyOnWriteArrayList<>(sorted));
    }

    private static String compositeKey(String sourceId, String listenerId) {
        return sourceId + "::" + listenerId;
    }

    /** 简单的占位符替换，按顺序用 args 替换 pattern 中的 {}。返回格式化后的字符串。 */
    private String formatTranslated(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0) return pattern;
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < pattern.length()) {
            int j = pattern.indexOf("{}", i);
            if (j == -1) {
                sb.append(pattern, i, pattern.length());
                break;
            }
            sb.append(pattern, i, j);
            sb.append(args[argIndex] == null ? "null" : args[argIndex].toString());
            argIndex = Math.min(argIndex + 1, args.length - 1);
            i = j + 2;
        }
        return sb.toString();
    }

    /**
     * 已注册的单个事件处理器的内部记录，负责把反射调用适配成一次派发调用。
     *
     * <p>支持的处理器签名在注册期解析并固化为具体行为，派发期不再做任何反射判断：
     * <ul>
     *   <li>{@code void m(E)} —— 同步完成</li>
     *   <li>{@code void m(E, Continuation)} —— 必须自行恢复</li>
     *   <li>{@code EventTask m(E)} —— 由返回值决定是否暂停</li>
     *   <li>{@code EventTask m(E, Continuation)} —— 由返回值决定，并可直接使用该凭据</li>
     * </ul>
     *
     * @param sourceId        来源标识（插件 ID 或内核标识）
     * @param listenerId      监听器 ID
     * @param listener        监听器实例
     * @param method          被 {@link EventHandler} 标注的处理方法
     * @param eventType       该方法监听的事件类型
     * @param priority        优先级
     * @param acceptsCancelled 事件被取消后是否仍然调用
     * @param polymorphic     是否启用多态匹配
     * @param wantsContinuation 方法是否接收 {@link Continuation} 参数
     * @param returnsTask     方法是否返回 {@link EventTask}
     * @param requiresAsync   是否要求换线程执行
     */
    record Invocation(
            String sourceId,
            String listenerId,
            EventListener listener,
            Method method,
            Class<? extends IStarlightEvent> eventType,
            EventPriority priority,
            boolean acceptsCancelled,
            boolean polymorphic,
            boolean wantsContinuation,
            boolean returnsTask,
            boolean requiresAsync
    ) {

        /**
         * 处理器标识，用于日志。
         *
         * <p>返回值是<b>单个</b>字符串（{@code 来源ID::监听器ID#方法名}），对应翻译文案里的
         * <b>一个</b> {@code {}} 占位符。不要试图把它拆成多个占位符的参数。
         *
         * @return 形如 {@code 来源ID::监听器ID#方法名} 的标识
         */
        String describe() {
            return sourceId + "::" + listenerId + "#" + method.getName();
        }

        /**
         * 调用处理器，返回它声明的任务。
         *
         * <p>返回 {@code null} 表示处理器已同步完成，无需暂停。
         *
         * @param event            事件实例
         * @param continuation     供处理器暂停派发使用的凭据
         * @param translateManager 用于输出告警的翻译管理器
         * @return 处理器返回的任务；返回 {@code void} 或返回 {@code null} 时为 {@code null}
         * @throws Throwable 处理器自身抛出的异常
         */
        EventTask complete(IStarlightEvent event, Continuation continuation,
                           TranslateManager translateManager) throws Throwable {
            Object result = wantsContinuation
                    ? method.invoke(listener, event, continuation)
                    : method.invoke(listener, event);

            if (!returnsTask) {
                return null;
            }
            if (result == null) {
                // 声明返回 EventTask 却给出 null：等同于同步完成。
                // 不能静默忽略，否则该处理器看起来"暂停"了却永远不会恢复。
                log.warn(translateManager.translate("starlight.logging.warn.event.handler_returned_null"),
                        listener.getClass().getName(), method.getName());
                return null;
            }
            return (EventTask) result;
        }
    }
}

