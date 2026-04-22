import io.slidermc.starlight.api.event.*;
import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.executor.ProxyExecutors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class EventManagerTest {

    private EventManager manager;
    private ProxyExecutors proxyExecutors;

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

    @BeforeEach
    public void setUp() {
        proxyExecutors = new ProxyExecutors();
        manager = new EventManager(
                proxyExecutors.getEventExecutor(),
                new io.slidermc.starlight.api.translate.TranslateManager()
        );
    }

    @Test
    public void testRegister() {
        List<String> called = new ArrayList<>();

        manager.register("test", new TestHandler(called));
        manager.fire(new TestCancellableEvent());

        assertTrue(called.contains("onCancellable"), "精确匹配处理器应被调用");
    }

    @Test
    public void testIgnoreCancelledSkipsHandler() {
        List<String> called = new ArrayList<>();

        manager.register("test", new IgnoreCancelledHandler(called));

        TestCancellableEvent event = new TestCancellableEvent();
        event.setCancelled(true);
        manager.fire(event);

        assertFalse(called.contains("ignoreCancelledHandler"),
                "ignoreCancelled=true 的处理器在事件已取消时不应被调用");
        assertTrue(called.contains("alwaysHandler"),
                "ignoreCancelled=false（默认）的处理器即使事件已取消也应被调用");
    }

    @Test
    public void testIgnoreCancelledAllowsWhenNotCancelled() {
        List<String> called = new ArrayList<>();

        manager.register("test", new IgnoreCancelledHandler(called));

        manager.fire(new TestCancellableEvent());

        assertTrue(called.contains("ignoreCancelledHandler"),
                "ignoreCancelled=true 的处理器在事件未取消时应正常调用");
        assertTrue(called.contains("alwaysHandler"), "默认处理器应被调用");
    }

    @Test
    public void testPolymorphicMatching() {
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
        List<String> called = new ArrayList<>();

        manager.register("test", new TestHandler(called));
        manager.unregister("test");
        manager.fire(new TestCancellableEvent());

        assertTrue(called.isEmpty(), "注销后处理器不应被调用");
    }

    // ==================== 优先级顺序测试 ====================

    @Test
    public void testEventPriorityOrder() {
        List<String> executionOrder = new CopyOnWriteArrayList<>();

        manager.register("priority-test", new PriorityHandler(executionOrder));

        manager.fire(new TestSimpleEvent());

        // 预期顺序：HIGHEST -> HIGH -> NORMAL -> LOW -> LOWEST -> MONITOR
        assertEquals(6, executionOrder.size(), "所有优先级的处理器都应被调用");

        assertEquals("HIGHEST", executionOrder.get(0), "HIGHEST 应最先执行");
        assertEquals("HIGH", executionOrder.get(1), "HIGH 应在 HIGHEST 之后执行");
        assertEquals("NORMAL", executionOrder.get(2), "NORMAL 应在 HIGH 之后执行");
        assertEquals("LOW", executionOrder.get(3), "LOW 应在 NORMAL 之后执行");
        assertEquals("LOWEST", executionOrder.get(4), "LOWEST 应在 LOW 之后执行");
        assertEquals("MONITOR", executionOrder.get(5), "MONITOR 应最后执行");
    }

    @Test
    public void testPriorityWithIgnoreCancelled() {
        List<String> executionOrder = new CopyOnWriteArrayList<>();

        manager.register("priority-ignore-test", new PriorityIgnoreCancelledHandler(executionOrder));

        TestCancellableEvent event = new TestCancellableEvent();
        event.setCancelled(true);
        manager.fire(event);

        // HIGHEST 设置了 ignoreCancelled=false，应被调用
        // NORMAL 设置了 ignoreCancelled=true，应被跳过
        // MONITOR 设置了 ignoreCancelled=false，应被调用
        assertEquals(2, executionOrder.size(), "只有 ignoreCancelled=false 的处理器应被调用");
        assertEquals("HIGHEST", executionOrder.get(0), "HIGHEST 应被调用");
        assertEquals("MONITOR", executionOrder.get(1), "MONITOR 应被调用");
        assertFalse(executionOrder.contains("NORMAL"), "NORMAL 设置了 ignoreCancelled=true，应被跳过");
    }

    // ==================== 异步派发测试 ====================

    @Test
    public void testFireAsyncExecutesHandlers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> called = new CopyOnWriteArrayList<>();

        manager.register("async-test", new AsyncTestHandler(called, latch));

        CompletableFuture<TestSimpleEvent> future = manager.fireAsync(new TestSimpleEvent());

        // 等待异步完成，最多 5 秒
        TestSimpleEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "处理器应在异步线程中被调用");
        assertTrue(called.contains("onSimpleEvent"), "异步派发应调用处理器");
    }

    @Test
    public void testFireAsyncWithCustomExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> called = new CopyOnWriteArrayList<>();
        java.util.concurrent.ExecutorService customExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

        try {
            manager.register("async-custom-executor", new AsyncTestHandler(called, latch));

            CompletableFuture<TestSimpleEvent> future = manager.fireAsync(new TestSimpleEvent(), customExecutor);

            TestSimpleEvent result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "处理器应在自定义线程池中被调用");
            assertTrue(called.contains("onSimpleEvent"), "异步派发应调用处理器");
        } finally {
            customExecutor.shutdown();
        }
    }

    @Test
    public void testFireAsyncPreservesPriorityOrder() throws Exception {
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(6); // 6个处理器

        manager.register("async-priority-test", new AsyncPriorityHandler(executionOrder, latch));

        CompletableFuture<TestSimpleEvent> future = manager.fireAsync(new TestSimpleEvent());

        // 等待所有处理器完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有处理器应在超时前完成");
        TestSimpleEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);

        // 验证顺序与同步路径一致
        assertEquals(6, executionOrder.size(), "所有优先级的处理器都应被调用");
        assertEquals("HIGHEST", executionOrder.get(0), "异步执行中 HIGHEST 应最先");
        assertEquals("HIGH", executionOrder.get(1), "异步执行中 HIGH 应在 HIGHEST 之后");
        assertEquals("NORMAL", executionOrder.get(2), "异步执行中 NORMAL 应在 HIGH 之后");
        assertEquals("LOW", executionOrder.get(3), "异步执行中 LOW 应在 NORMAL 之后");
        assertEquals("LOWEST", executionOrder.get(4), "异步执行中 LOWEST 应在 LOW 之后");
        assertEquals("MONITOR", executionOrder.get(5), "异步执行中 MONITOR 应最后");
    }

    @Test
    public void testFireAsyncWithIgnoreCancelled() throws Exception {
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2); // 只有2个处理器会被调用

        manager.register("async-ignore-test", new AsyncIgnoreCancelledHandler(executionOrder, latch));

        TestCancellableEvent event = new TestCancellableEvent();
        event.setCancelled(true);
        CompletableFuture<TestCancellableEvent> future = manager.fireAsync(event);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "被调用的处理器应在超时前完成");
        TestCancellableEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(2, executionOrder.size(), "只有 ignoreCancelled=false 的处理器应被调用");
        assertEquals("HIGHEST", executionOrder.get(0), "HIGHEST 应被调用");
        assertEquals("MONITOR", executionOrder.get(1), "MONITOR 应被调用");
        assertFalse(executionOrder.contains("NORMAL"), "NORMAL 设置了 ignoreCancelled=true，应被跳过");
    }

    @Test
    public void testFireAsyncPolymorphicMatching() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.register("async-polymorphic-test", new AsyncPolymorphicHandler(called, latch));

        CompletableFuture<ChildEvent> future = manager.fireAsync(new ChildEvent());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "处理器应在超时前完成");
        ChildEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(called.contains("onBaseEvent"), "异步派发中 polymorphic=true 应匹配子类事件");
        assertFalse(called.contains("onChildExact"), "异步派发中精确匹配不应匹配子类事件");
    }

    @Test
    public void testFireAsyncMultipleListeners() throws Exception {
        List<String> called = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3); // 3个处理器

        manager.register("async-listener1", new AsyncTestHandler(called, latch));
        manager.register("async-listener2", new AsyncTestHandler2(called, latch));
        manager.register("async-listener3", new AsyncTestHandler3(called, latch));

        CompletableFuture<TestSimpleEvent> future = manager.fireAsync(new TestSimpleEvent());

        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有处理器应在超时前完成");
        TestSimpleEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(called.contains("onSimpleEvent"), "处理器1应被调用");
        assertTrue(called.contains("onSimpleEvent2"), "处理器2应被调用");
        assertTrue(called.contains("onSimpleEvent3"), "处理器3应被调用");
        assertEquals(3, called.size(), "所有3个处理器都应被调用");
    }

    @Test
    public void testFireAsyncExceptionDoesNotAffectOtherHandlers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);  // 只需要等待 normalHandler
        List<String> called = new CopyOnWriteArrayList<>();

        manager.register("async-exception-test", new ExceptionThrowingHandler(called, latch));

        CompletableFuture<TestSimpleEvent> future = manager.fireAsync(new TestSimpleEvent());

        // 即使有处理器抛出异常，future 仍应正常完成（异常被记录但不会传播到 future）
        TestSimpleEvent result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "正常处理器应被调用");
        assertTrue(called.contains("normalHandler"), "正常处理器应被调用");
        // 异常处理器可能被调用也可能因异常而部分执行，但不应导致整个事件派发失败
        // 注意：throwingHandler 在抛出异常前会先 called.add("throwingHandler")
        assertTrue(called.contains("throwingHandler"), "异常处理器也应被调用（在抛出异常前已记录）");
    }

    // ==================== 测试辅助类 ====================

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

        @EventHandler(ignoreCancelled = true)
        public void ignoreCancelledHandler(TestCancellableEvent event) {
            called.add("ignoreCancelledHandler");
        }

        @EventHandler
        public void alwaysHandler(TestCancellableEvent event) {
            called.add("alwaysHandler");
        }
    }

    static class PolymorphicHandler implements EventListener {
        private final List<String> called;

        PolymorphicHandler(List<String> called) { this.called = called; }

        @EventHandler(polymorphic = true)
        public void onBaseEvent(BaseEvent event) {
            called.add("onBaseEvent");
        }

        @EventHandler
        public void onChildExact(BaseEvent event) {
            called.add("onChildExact");
        }
    }

    // 优先级测试处理器
    static class PriorityHandler implements EventListener {
        private final List<String> executionOrder;

        PriorityHandler(List<String> executionOrder) {
            this.executionOrder = executionOrder;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onHighest(TestSimpleEvent event) {
            executionOrder.add("HIGHEST");
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onHigh(TestSimpleEvent event) {
            executionOrder.add("HIGH");
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onNormal(TestSimpleEvent event) {
            executionOrder.add("NORMAL");
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onLow(TestSimpleEvent event) {
            executionOrder.add("LOW");
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLowest(TestSimpleEvent event) {
            executionOrder.add("LOWEST");
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMonitor(TestSimpleEvent event) {
            executionOrder.add("MONITOR");
        }
    }

    // 优先级与 ignoreCancelled 组合测试
    static class PriorityIgnoreCancelledHandler implements EventListener {
        private final List<String> executionOrder;

        PriorityIgnoreCancelledHandler(List<String> executionOrder) {
            this.executionOrder = executionOrder;
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onHighest(TestCancellableEvent event) {
            executionOrder.add("HIGHEST");
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onNormal(TestCancellableEvent event) {
            executionOrder.add("NORMAL");
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onMonitor(TestCancellableEvent event) {
            executionOrder.add("MONITOR");
        }
    }

    // 异步测试处理器
    static class AsyncTestHandler implements EventListener {
        private final List<String> called;
        private final CountDownLatch latch;

        AsyncTestHandler(List<String> called, CountDownLatch latch) {
            this.called = called;
            this.latch = latch;
        }

        @EventHandler
        public void onSimpleEvent(TestSimpleEvent event) {
            called.add("onSimpleEvent");
            latch.countDown();
        }
    }

    static class AsyncTestHandler2 implements EventListener {
        private final List<String> called;
        private final CountDownLatch latch;

        AsyncTestHandler2(List<String> called, CountDownLatch latch) {
            this.called = called;
            this.latch = latch;
        }

        @EventHandler
        public void onSimpleEvent2(TestSimpleEvent event) {
            called.add("onSimpleEvent2");
            latch.countDown();
        }
    }

    static class AsyncTestHandler3 implements EventListener {
        private final List<String> called;
        private final CountDownLatch latch;

        AsyncTestHandler3(List<String> called, CountDownLatch latch) {
            this.called = called;
            this.latch = latch;
        }

        @EventHandler
        public void onSimpleEvent3(TestSimpleEvent event) {
            called.add("onSimpleEvent3");
            latch.countDown();
        }
    }

    // 异步优先级测试处理器
    static class AsyncPriorityHandler implements EventListener {
        private final List<String> executionOrder;
        private final CountDownLatch latch;

        AsyncPriorityHandler(List<String> executionOrder, CountDownLatch latch) {
            this.executionOrder = executionOrder;
            this.latch = latch;
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onHighest(TestSimpleEvent event) {
            executionOrder.add("HIGHEST");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.HIGH)
        public void onHigh(TestSimpleEvent event) {
            executionOrder.add("HIGH");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onNormal(TestSimpleEvent event) {
            executionOrder.add("NORMAL");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onLow(TestSimpleEvent event) {
            executionOrder.add("LOW");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLowest(TestSimpleEvent event) {
            executionOrder.add("LOWEST");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMonitor(TestSimpleEvent event) {
            executionOrder.add("MONITOR");
            latch.countDown();
        }
    }

    // 异步 ignoreCancelled 测试处理器
    static class AsyncIgnoreCancelledHandler implements EventListener {
        private final List<String> executionOrder;
        private final CountDownLatch latch;

        AsyncIgnoreCancelledHandler(List<String> executionOrder, CountDownLatch latch) {
            this.executionOrder = executionOrder;
            this.latch = latch;
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
        public void onHighest(TestCancellableEvent event) {
            executionOrder.add("HIGHEST");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        public void onNormal(TestCancellableEvent event) {
            executionOrder.add("NORMAL");
            latch.countDown();
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
        public void onMonitor(TestCancellableEvent event) {
            executionOrder.add("MONITOR");
            latch.countDown();
        }
    }

    // 异步多态测试处理器
    static class AsyncPolymorphicHandler implements EventListener {
        private final List<String> called;
        private final CountDownLatch latch;

        AsyncPolymorphicHandler(List<String> called, CountDownLatch latch) {
            this.called = called;
            this.latch = latch;
        }

        @EventHandler(polymorphic = true)
        public void onBaseEvent(BaseEvent event) {
            called.add("onBaseEvent");
            latch.countDown();
        }

        @EventHandler
        public void onChildExact(BaseEvent event) {
            called.add("onChildExact");
            latch.countDown();
        }
    }

    static class ExceptionThrowingHandler implements EventListener {
        private final List<String> called;
        private final CountDownLatch latch;

        ExceptionThrowingHandler(List<String> called, CountDownLatch latch) {
            this.called = called;
            this.latch = latch;
        }

        @EventHandler
        public void throwingHandler(TestSimpleEvent event) {
            called.add("throwingHandler");
            // 注意：这里不调用 latch.countDown()，因为抛出异常后不会执行后续代码
            // 但这没关系，因为我们只等待 normalHandler
            throw new RuntimeException("Test exception in event handler");
        }

        @EventHandler
        public void normalHandler(TestSimpleEvent event) {
            called.add("normalHandler");
            latch.countDown();  // 只有这个处理器会 count down
        }
    }
}