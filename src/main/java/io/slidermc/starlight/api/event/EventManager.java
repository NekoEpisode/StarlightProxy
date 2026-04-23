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
    private final Map<Class<? extends IStarlightEvent>, List<RegisteredHandler>> handlerMap = new ConcurrentHashMap<>();

    /**
     * 来源ID（{@code "pluginId::listenerId"} 或 {@code "__starlight_kernel__::listenerId"}）
     * 到该监听器产生的所有 {@link RegisteredHandler} 的映射，用于快速注销。
     */
    private final Map<String, List<RegisteredHandler>> listenerIndex = new ConcurrentHashMap<>();

    /**
     * 插件 ID 到该插件所有监听器来源 key 的映射，用于批量注销。
     */
    private final Map<String, Set<String>> pluginListenerKeys = new ConcurrentHashMap<>();

    /**
     * 默认用于异步派发的 Executor。可通过构造器注入（推荐在 {@link io.slidermc.starlight.StarlightProxy}
     * 构造时传入 {@link io.slidermc.starlight.executor.ProxyExecutors#getEventExecutor()}）。
     */
    private final Executor defaultExecutor;
    private final TranslateManager translateManager;

    /**
     * 构造一个使用公共 ForkJoinPool 作为默认异步执行器的 EventManager。
     */
    public EventManager(TranslateManager translateManager) {
        this(ForkJoinPool.commonPool(), translateManager);
    }

    /**
     * 构造一个使用指定 Executor 作为默认异步执行器的 EventManager。
     *
     * @param defaultExecutor 默认用于 {@link #fireAsync(IStarlightEvent)} 的 Executor
     */
    public EventManager(Executor defaultExecutor, TranslateManager translateManager) {
        this.defaultExecutor = Objects.requireNonNull(defaultExecutor, "defaultExecutor");
        this.translateManager = Objects.requireNonNull(translateManager, "translateManager");
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
        String pluginIdForLog = null;

        registryLock.lock();
        try {
            Set<String> keys = pluginListenerKeys.remove(pluginId);
            if (keys == null) return;
            for (String key : keys) {
                List<RegisteredHandler> handlers = listenerIndex.remove(key);
                if (handlers == null) continue;
                for (RegisteredHandler handler : handlers) {
                    List<RegisteredHandler> list = handlerMap.get(handler.eventType());
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
     * <p>默认情况下（{@code polymorphic = false}）仅精确匹配事件类型；
     * 若处理器在注解中声明了 {@code polymorphic = true}，则该处理器采用继承关系匹配，
     * 事件类型为其参数类型的子类时同样会被触发。
     *
     * <p>若事件实现了 {@link ICancellableEvent}，且某处理器设置了 {@code ignoreCancelled = true}，
     * 则该事件被取消后该处理器将被跳过。
     *
     * @param event 要派发的事件
     * @param <E>   事件类型
     * @return 传入的事件实例（便于链式调用）
     */
    public <E extends IStarlightEvent> E fire(E event) {
        Class<? extends IStarlightEvent> exactType = event.getClass();
        List<RegisteredHandler> allMatches = new ArrayList<>();

        for (Map.Entry<Class<? extends IStarlightEvent>, List<RegisteredHandler>> entry : handlerMap.entrySet()) {
            boolean isExact = entry.getKey() == exactType;
            for (RegisteredHandler handler : entry.getValue()) {
                // 精确匹配的处理器始终包含；多态处理器额外检查继承关系
                if (isExact || (handler.polymorphic() && entry.getKey().isAssignableFrom(exactType))) {
                    allMatches.add(handler);
                }
            }
        }

        if (allMatches.isEmpty()) return event;

        allMatches.sort(Comparator.comparingInt(h -> -h.priority().getOrder()));

        for (RegisteredHandler handler : allMatches) {
            if (handler.ignoreCancelled()
                    && event instanceof ICancellableEvent c
                    && c.isCancelled()) {
                continue;
            }
            try {
                handler.method().invoke(handler.listener(), event);
            } catch (Exception e) {
                log.error(translateManager.translate("starlight.logging.error.event.handler_threw"),
                        handler.sourceId(), handler.listenerId(),
                        event.getClass().getSimpleName(), e);
            }
        }
        return event;
    }

    /**
     * 异步派发事件，使用默认公共线程池（ForkJoinPool.commonPool）。
     * 派发序列在异步线程中按顺序执行，语义等同于 {@link #fire(IStarlightEvent)}，
     * 但不阻塞调用方线程。
     *
     * @param event 要派发的事件
     * @param <E>   事件类型
     * @return 在异步执行完成后完成的 CompletableFuture，返回传入事件实例
     */
    public <E extends IStarlightEvent> CompletableFuture<E> fireAsync(E event) {
        return CompletableFuture.supplyAsync(() -> fire(event), defaultExecutor);
    }

    /**
     * 使用指定的 {@link Executor} 在异步线程中顺序执行事件派发。
     * 建议传入 {@link io.slidermc.starlight.executor.ProxyExecutors#getEventExecutor()}，
     * 以使用虚拟线程池执行事件处理。
     *
     * @param event    要派发的事件
     * @param executor 用于执行的线程池
     * @param <E>      事件类型
     * @return 在异步执行完成后完成的 CompletableFuture，返回传入事件实例
     */
    public <E extends IStarlightEvent> CompletableFuture<E> fireAsync(E event, Executor executor) {
        return CompletableFuture.supplyAsync(() -> fire(event), executor);
    }

    /**
     * 返回指定事件类型当前已注册的处理器数量，用于调试与测试。
     *
     * @param eventType 事件类型
     * @return 处理器数量
     */
    public int getHandlerCount(Class<? extends IStarlightEvent> eventType) {
        List<RegisteredHandler> list = handlerMap.get(eventType);
        return list == null ? 0 : list.size();
    }

    private void registerInternal(String sourceId, String listenerId, io.slidermc.starlight.api.event.EventListener listener) {
        String key = compositeKey(sourceId, listenerId);
        List<RegisteredHandler> existing = listenerIndex.putIfAbsent(key, List.of());
        if (existing != null) {
            String pattern = translateManager.translate("starlight.logging.error.event.listener_id_duplicate");
            throw new IllegalArgumentException(formatTranslated(pattern, listenerId, sourceId));
        }

        List<RegisteredHandler> discovered = new ArrayList<>();
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) continue;
            if (method.getParameterCount() != 1) {
                log.warn(translateManager.translate("starlight.logging.warn.event.handler_param_count_invalid"),
                        listener.getClass().getName(), method.getName());
                continue;
            }
            Class<?> paramType = method.getParameterTypes()[0];
            if (!IStarlightEvent.class.isAssignableFrom(paramType)) {
                log.warn(translateManager.translate("starlight.logging.warn.event.handler_param_type_invalid"),
                        listener.getClass().getName(), method.getName(), paramType.getName());
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends IStarlightEvent> eventType = (Class<? extends IStarlightEvent>) paramType;
            method.setAccessible(true);
            RegisteredHandler handler = new RegisteredHandler(
                    sourceId, listenerId, listener, method, eventType,
                    annotation.priority(), annotation.ignoreCancelled(), annotation.polymorphic()
            );
            discovered.add(handler);
        }

        if (discovered.isEmpty()) {
            listenerIndex.remove(key);
            log.warn(translateManager.translate("starlight.logging.warn.event.no_valid_handlers"),
                    listener.getClass().getName(), sourceId);
            return;
        }

        listenerIndex.put(key, discovered);
        for (RegisteredHandler handler : discovered) {
            List<RegisteredHandler> newList = handlerMap.computeIfAbsent(handler.eventType(), k -> new CopyOnWriteArrayList<>());
            newList.add(handler);
            replaceWithSortedCopy(handler.eventType(), newList);
        }
        log.debug("已注册监听器 [{}] 来自 [{}]，包含 {} 个处理器",
                listenerId, sourceId, discovered.size());
    }

    private void unregisterInternal(String sourceId, String listenerId) {
        String key = compositeKey(sourceId, listenerId);
        List<RegisteredHandler> handlers = listenerIndex.remove(key);
        if (handlers == null) {
            log.warn(translateManager.translate("starlight.logging.warn.event.unregister_nonexistent"), listenerId, sourceId);
            return;
        }
        for (RegisteredHandler handler : handlers) {
            List<RegisteredHandler> list = handlerMap.get(handler.eventType());
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
    private void replaceWithSortedCopy(Class<? extends IStarlightEvent> eventType, List<RegisteredHandler> currentList) {
        List<RegisteredHandler> sorted = new ArrayList<>(currentList);
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
     * 已注册的单个事件处理器的内部记录。
     *
     * @param sourceId        来源标识（插件 ID 或内核标识）
     * @param listenerId      监听器 ID
     * @param listener        监听器实例
     * @param method          被 {@link EventHandler} 标注的处理方法
     * @param eventType       该方法监听的事件类型
     * @param priority        优先级
     * @param ignoreCancelled 是否在事件被取消时跳过
     * @param polymorphic     是否启用多态匹配
     */
    private record RegisteredHandler(
            String sourceId,
            String listenerId,
            EventListener listener,
            Method method,
            Class<? extends IStarlightEvent> eventType,
            EventPriority priority,
            boolean ignoreCancelled,
            boolean polymorphic
    ) {}
}

