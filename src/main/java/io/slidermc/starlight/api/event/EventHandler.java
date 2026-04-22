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
 *   <li>方法返回值为 {@code void}</li>
 *   <li>方法恰好有一个参数，参数类型为 {@link IStarlightEvent} 的子类型</li>
 * </ul>
 *
 * <p>示例：
 * <pre>{@code
 * public class MyListener implements EventListener {
 *
 *     @EventHandler
 *     public void onPlayerLogin(PlayerLoginEvent event) {
 *         // 处理玩家登录事件
 *     }
 *
 *     @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 *     public void onPlayerChat(PlayerChatEvent event) {
 *         // 高优先级处理，已取消的事件将被跳过
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * 处理器的执行优先级，默认为 {@link EventPriority#NORMAL}。
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * 若为 {@code true}，当事件已被取消（实现了 {@link ICancellableEvent} 且 {@code isCancelled() == true}）时，
     * 跳过此处理器。默认为 {@code false}。
     */
    boolean ignoreCancelled() default false;

    /**
     * 是否启用多态匹配。
     *
     * <p>默认为 {@code false}，即精确匹配：仅当派发的事件类型与方法参数类型完全一致时才调用此处理器。
     *
     * <p>若设为 {@code true}，则采用继承关系匹配：派发的事件类型为方法参数类型的子类时也会触发此处理器。
     * 适用于"监听某一类事件"或"监听所有事件"的场景，例如参数类型为 {@link IStarlightEvent} 的通用处理器。
     *
     * <p>示例：
     * <pre>{@code
     * // 监听 PlayerEvent 及其所有子类
     * @EventHandler(polymorphic = true)
     * public void onAnyPlayerEvent(PlayerEvent event) { }
     * }</pre>
     */
    boolean polymorphic() default false;
}

