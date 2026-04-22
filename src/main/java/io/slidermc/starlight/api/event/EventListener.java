package io.slidermc.starlight.api.event;

/**
 * 事件监听器的标记接口。
 *
 * <p>所有希望接收事件的类都应实现此接口，并在需要监听的方法上添加 {@link EventHandler} 注解。
 * 实现类本身不需要实现任何方法，仅用于标识该对象可作为监听器注册到 {@link EventManager}。
 *
 * <p>监听器可以自由地实现多个事件处理方法，每个方法对应一个事件类型。插件和内核实现的事件监听器
 * 均应实现此接口。
 */
public interface EventListener {
}

