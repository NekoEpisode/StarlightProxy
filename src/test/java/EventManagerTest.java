import io.slidermc.starlight.api.event.*;
import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EventManagerTest {

    /** 用于断言的具体可取消事件。 */
    static class TestCancellableEvent implements ICancellableEvent {
        private boolean cancelled = false;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** 用于断言的普通事件。 */
    static class TestSimpleEvent implements IStarlightEvent {}

    /** 用于多态匹配断言的基类事件。 */
    static abstract class BaseEvent implements IStarlightEvent {}

    /** BaseEvent 的子类。 */
    static class ChildEvent extends BaseEvent {}

    @Test
    public void testRegister() {
        EventManager manager = new EventManager(new io.slidermc.starlight.executor.ProxyExecutors().getEventExecutor(), new io.slidermc.starlight.api.translate.TranslateManager());
        List<String> called = new ArrayList<>();

        manager.register("test", new TestHandler(called));
        manager.fire(new TestCancellableEvent());

        assertTrue(called.contains("onCancellable"), "精确匹配处理器应被调用");
    }

    @Test
    public void testIgnoreCancelledSkipsHandler() {
        EventManager manager = new EventManager(new io.slidermc.starlight.executor.ProxyExecutors().getEventExecutor(), new io.slidermc.starlight.api.translate.TranslateManager());
        List<String> called = new ArrayList<>();

        manager.register("test", new IgnoreCancelledHandler(called));

        TestCancellableEvent event = new TestCancellableEvent();
        ((ICancellableEvent) event).setCancelled(true);
        manager.fire(event);

        assertFalse(called.contains("ignoreCancelledHandler"),
                "ignoreCancelled=true 的处理器在事件已取消时不应被调用");
        assertTrue(called.contains("alwaysHandler"),
                "ignoreCancelled=false（默认）的处理器即使事件已取消也应被调用");
    }

    @Test
    public void testIgnoreCancelledAllowsWhenNotCancelled() {
        EventManager manager = new EventManager(new io.slidermc.starlight.executor.ProxyExecutors().getEventExecutor(), new io.slidermc.starlight.api.translate.TranslateManager());
        List<String> called = new ArrayList<>();

        manager.register("test", new IgnoreCancelledHandler(called));

        manager.fire(new TestCancellableEvent());

        assertTrue(called.contains("ignoreCancelledHandler"),
                "ignoreCancelled=true 的处理器在事件未取消时应正常调用");
        assertTrue(called.contains("alwaysHandler"), "默认处理器应被调用");
    }

    @Test
    public void testPolymorphicMatching() {
        EventManager manager = new EventManager(new io.slidermc.starlight.executor.ProxyExecutors().getEventExecutor(), new io.slidermc.starlight.api.translate.TranslateManager());
        List<String> called = new ArrayList<>();

        manager.register("test", new PolymorphicHandler(called));
        manager.fire(new ChildEvent());

        assertTrue(called.contains("onBaseEvent"),
                "polymorphic=true 的处理器应匹配子类事件");
        assertFalse(called.contains("onChildExact"),
                "精确处理器声明 BaseEvent，不应匹配 ChildEvent（非多态默认行为验证）");
    }

    @Test
    public void testUnregister() {
        EventManager manager = new EventManager(new io.slidermc.starlight.executor.ProxyExecutors().getEventExecutor(), new io.slidermc.starlight.api.translate.TranslateManager());
        List<String> called = new ArrayList<>();

        manager.register("test", new TestHandler(called));
        manager.unregister("test");
        manager.fire(new TestCancellableEvent());

        assertTrue(called.isEmpty(), "注销后处理器不应被调用");
    }

    static class TestHandler implements EventListener {
        private final List<String> called;

        TestHandler(List<String> called) { this.called = called; }

        @EventHandler
        public void onCancellable(TestCancellableEvent event) {
            called.add("onCancellable");
        }
        @EventHandler(polymorphic = true)
        public void onAny(IStarlightEvent event) {
            called.add("onAny");
        }
    }

    static class IgnoreCancelledHandler implements EventListener {
        private final List<String> called;

        IgnoreCancelledHandler(List<String> called) { this.called = called; }

        /** 事件已取消时跳过。 */
        @EventHandler(ignoreCancelled = true)
        public void ignoreCancelledHandler(TestCancellableEvent event) {
            called.add("ignoreCancelledHandler");
        }

        /** 无论是否取消都调用。 */
        @EventHandler
        public void alwaysHandler(TestCancellableEvent event) {
            called.add("alwaysHandler");
        }
    }

    static class PolymorphicHandler implements EventListener {
        private final List<String> called;

        PolymorphicHandler(List<String> called) { this.called = called; }

        /** polymorphic=true，BaseEvent 及其子类均匹配。 */
        @EventHandler(polymorphic = true)
        public void onBaseEvent(BaseEvent event) {
            called.add("onBaseEvent");
        }

        /** 默认精确匹配，仅 BaseEvent 本身触发，ChildEvent 不触发。 */
        @EventHandler
        public void onChildExact(BaseEvent event) {
            called.add("onChildExact");
        }
    }
}
