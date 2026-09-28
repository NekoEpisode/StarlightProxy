package io.slidermc.starlight.api.event;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为事件处理器。
 *
 * <p>被标记的方法必须满足：
 * <ul>
 *   <li>所在类实现 {@link EventListener}</li>
 *   <li>方法为 {@code public}</li>
 *   <li>第一个参数为事件类型，可选地追加一个 {@link Continuation} 参数</li>
 *   <li>返回值为 {@code void} 或 {@link EventTask}</li>
 * </ul>
 *
 * <p>支持的签名：
 * <pre>{@code
 * // 同步处理
 * @EventHandler
 * public void onPlayerLogin(PlayerLoginEvent event) { }
 *
 * // 需要异步操作后恢复派发
 * @EventHandler
 * public EventTask onPlayerLogin(PlayerLoginEvent event, Continuation continuation) {
 *     lookup(event.getPlayer().getUniqueId()).whenComplete((result, error) -> {
 *         if (error != null) continuation.resumeWithException(error);
 *         else { event.setSomething(result); continuation.resume(); }
 *     });
 *     return EventTask.withContinuation(c -> {});
 * }
 *
 * // 阻塞型处理，声明运行在事件执行器（虚拟线程）上
 * @EventHandler(async = true)
 * public void onProxyPing(ProxyPingEvent event) {
 *     event.setDescription(blockingDatabaseQuery());
 * }
 * }</pre>
 *
 * <p>事件被取消后派发立即终止（见 {@link ICancellableEvent}）。需要观察已取消事件的处理器
 * 应声明 {@link #acceptsCancelled()}。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * 处理器的执行优先级，默认为 {@link EventPriority#NORMAL}。
     *
     * <p>优先级只决定处理器之间的先后顺序，不受暂停影响：处理器暂停后，优先级更低的处理器
     * 仍会在它恢复之后才被调用。
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * 事件被取消后是否仍然调用此处理器。
     *
     * <p>默认为 {@code false}，即事件一旦被取消，本处理器会被跳过、且派发就此终止。
     * 声明为 {@code true} 的处理器用于只读观察最终状态，通常应使用
     * {@link EventPriority#MONITOR} 优先级。
     *
     * @return 已取消的事件也应调用此处理器时返回 {@code true}
     */
    boolean acceptsCancelled() default false;

    /**
     * 是否启用多态匹配。
     *
     * <p>默认为 {@code false}，即精确匹配：仅当派发的事件类型与方法参数类型完全一致时才调用。
     *
     * <p>若设为 {@code true}，则采用继承关系匹配：派发的事件类型为方法参数类型的子类时也会调用，
     * 适用于监听某一类事件或全部事件。
     *
     * @return 按继承关系匹配时返回 {@code true}
     */
    boolean polymorphic() default false;

    /**
     * 是否要求本处理器在事件执行器（虚拟线程）上执行。
     *
     * <p>默认为 {@code false}，处理器在派发调用方的线程上执行。若处理器会进行阻塞式调用
     * （网络请求、数据库查询、磁盘读取），应声明为 {@code true}，以免阻塞调用方线程。
     *
     * <p>注意：只要某个事件存在声明了 {@code true} 的处理器，该事件的整个派发都会在事件执行器
     * 上开始；其余处理器仍然按优先级顺序执行。
     *
     * @return 必须换线程执行时返回 {@code true}
     */
    boolean async() default false;
}
