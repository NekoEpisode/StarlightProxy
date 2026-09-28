package io.slidermc.starlight.api.event;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class EventDispatchTest {

    private EventManager eventManager;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        TranslateManager translateManager = new TranslateManager();
        translateManager.loadBuiltin();
        // 使用守护线程：即使某个测试未能恢复派发，JVM 也必须能够退出，
        // 否则 surefire 会一直等待非守护线程而表现为"测试卡住"
        executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("event-test-", 0).factory());
        eventManager = new EventManager(executor, translateManager);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void synchronousHandlerRunsBeforeFireReturns() {
        List<String> order = new ArrayList<>();
        eventManager.register("synchronous", new EventListener() {
            @EventHandler
            public void onSimple(SimpleEvent event) {
                order.add("handler");
            }
        });

        SimpleEvent event = new SimpleEvent();
        eventManager.fire(event).join();
        order.add("after-fire");

        assertEquals(List.of("handler", "after-fire"), order);
    }

    @Test
    void fireCompletesWithTheDispatchedEvent() {
        SimpleEvent event = new SimpleEvent();
        SimpleEvent completed = eventManager.fire(event).join();

        assertEquals(event, completed);
    }

    @Test
    void noHandlersStillCompletes() {
        SimpleEvent event = new SimpleEvent();
        assertNotNull(eventManager.fire(event).join());
    }

    @Test
    void handlersRunInPriorityOrder() {
        List<String> order = new ArrayList<>();
        eventManager.register("high", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onSimple(SimpleEvent event) {
                order.add("high");
            }
        });
        eventManager.register("lowest", new EventListener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onSimple(SimpleEvent event) {
                order.add("lowest");
            }
        });
        eventManager.register("normal", new EventListener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onSimple(SimpleEvent event) {
                order.add("normal");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("high", "normal", "lowest"), order);
    }

    @Test
    void suspendedHandlerBlocksFollowingHandlersUntilResumed() throws Exception {
        AtomicReference<Continuation> captured = new AtomicReference<>();
        List<String> order = new ArrayList<>();

        // 必须返回 EventTask：只有它能让处理器在"返回之后"恢复。
        // 返回 void 的方法只能同步恢复，不具备暂停能力。
        eventManager.register("suspending", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event, Continuation continuation) {
                captured.set(continuation);
                return EventTask.withContinuation(c -> {});
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        CompletableFuture<SimpleEvent> future = eventManager.fire(new SimpleEvent());

        assertNotNull(captured.get(), "处理器未被调用");
        assertFalse(future.isDone(), "挂起期间事件不应被标记为完成");
        assertTrue(order.isEmpty(), "后续处理器不应在恢复前执行");

        captured.get().resume();

        future.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("after"), order);
    }

    @Test
    void asyncResumeContinuesOnTheResumingThread() throws Exception {
        ExecutorService resumer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "resumer");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch captured = new CountDownLatch(1);
        AtomicReference<Continuation> continuation = new AtomicReference<>();
        AtomicReference<String> continuationThread = new AtomicReference<>();

        eventManager.register("suspending", new EventListener() {
            @EventHandler
            public EventTask onSimple(SimpleEvent event, Continuation c) {
                // 先返回任务、让派发真正暂停，之后才从其它线程恢复：
                // 只有"处理器返回之后"发生的恢复才由恢复线程继续派发。
                continuation.set(c);
                captured.countDown();
                return EventTask.withContinuation(x -> {});
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                continuationThread.set(Thread.currentThread().getName());
            }
        });

        CompletableFuture<SimpleEvent> future = eventManager.fire(new SimpleEvent());

        assertTrue(captured.await(5, TimeUnit.SECONDS), "处理器未被调用");
        assertFalse(future.isDone(), "处理器返回后派发应处于暂停状态");

        resumer.execute(continuation.get()::resume);

        future.get(5, TimeUnit.SECONDS);
        resumer.shutdownNow();

        assertEquals("resumer", continuationThread.get());
    }

    @Test
    void resumeWhenCompleteWaitsForTheFuture() throws Exception {
        CompletableFuture<String> pending = new CompletableFuture<>();
        List<String> order = new ArrayList<>();

        eventManager.register("awaiting", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                return EventTask.resumeWhenComplete(pending);
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        CompletableFuture<SimpleEvent> fired = eventManager.fire(new SimpleEvent());
        assertFalse(fired.isDone());

        pending.complete("value");

        fired.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("after"), order);
    }

    @Test
    void handlerMayResumeSynchronouslyWithoutScheduling() {
        List<String> order = new ArrayList<>();

        eventManager.register("sync-resume", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event, Continuation continuation) {
                continuation.resume();
                return EventTask.completed();
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void resumingTwiceIsRejected() {
        AtomicReference<Continuation> captured = new AtomicReference<>();

        eventManager.register("double-resume", new EventListener() {
            @EventHandler
            public void onSimple(SimpleEvent event, Continuation continuation) {
                captured.set(continuation);
            }
        });

        eventManager.fire(new SimpleEvent());
        captured.get().resume();

        assertThrows(IllegalStateException.class, captured.get()::resume);
    }

    @Test
    void throwingHandlerDoesNotStopDispatch() {
        List<String> order = new ArrayList<>();

        eventManager.register("throwing", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onSimple(SimpleEvent event) {
                throw new IllegalStateException("expected");
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void resumeWithExceptionStillContinues() {
        List<String> order = new ArrayList<>();

        eventManager.register("failing", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onSimple(SimpleEvent event, Continuation continuation) {
                continuation.resumeWithException(new IllegalStateException("expected"));
            }
        });
        eventManager.register("after", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void completedTaskDoesNotSuspendDispatch() {
        // EventTask.completed() 必须立即恢复派发。
        // 若它只是空操作（不调用 resume），返回它的处理器会把自己永久挂死。
        List<String> order = new ArrayList<>();

        eventManager.register("returns-completed", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                order.add("completed");
                return EventTask.completed();
            }
        });
        eventManager.register("after-completed", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("completed", "after"), order);
    }

    @Test
    void completedTaskFromAsyncHandlerCompletes() {
        List<String> order = new ArrayList<>();

        eventManager.register("async-completed", new EventListener() {
            @EventHandler(async = true)
            public EventTask onSimple(SimpleEvent event) {
                order.add("async");
                return EventTask.completed();
            }
        });
        eventManager.register("after-async-completed", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("async", "after"), order);
    }

    @Test
    void taskThatThrowsStillResumesDispatch() {
        // EventTask.of 必须在任务抛异常时也恢复派发，
        // 否则一次异常就会让登录流程永久挂起
        List<String> order = new ArrayList<>();

        eventManager.register("throwing-task", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                return EventTask.of(() -> {
                    throw new IllegalStateException("expected");
                });
            }
        });
        eventManager.register("after-throwing-task", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void asyncTaskThatThrowsStillResumesDispatch() {
        List<String> order = new ArrayList<>();

        eventManager.register("throwing-async-task", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                return EventTask.async(() -> {
                    throw new IllegalStateException("expected");
                });
            }
        });
        eventManager.register("after-throwing-async-task", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void taskReturningHandlerThatThrowsDoesNotHang() {
        // 处理器体抛出异常时必须继续派发：它已无法再恢复，等待 resume() 会永久挂起
        List<String> order = new ArrayList<>();

        eventManager.register("throws-before-task", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                throw new IllegalStateException("expected");
            }
        });
        eventManager.register("after-throwing-task-handler", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void taskReturningHandlerWithContinuationThatThrowsDoesNotHang() {
        List<String> order = new ArrayList<>();

        eventManager.register("throws-with-continuation", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event, Continuation continuation) {
                throw new IllegalStateException("expected");
            }
        });
        eventManager.register("after-throwing-continuation-handler", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void eventTaskThatThrowsDoesNotHang() {
        List<String> order = new ArrayList<>();

        eventManager.register("task-throws", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                return continuation -> {
                    throw new IllegalStateException("expected");
                };
            }
        });
        eventManager.register("after-task-throws", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void handlerReturningNullTaskDoesNotHang() {
        List<String> order = new ArrayList<>();

        eventManager.register("returns-null", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                return null;
            }
        });
        eventManager.register("after-null-task", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("after"), order);
    }

    @Test
    void fireSyncAcceptsCompletedTask() {
        // EventTask.completed() 是给同步处理器用的，fireSync 必须接受它
        List<String> order = new ArrayList<>();

        eventManager.register("sync-completed", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public EventTask onSimple(SimpleEvent event) {
                order.add("completed");
                return EventTask.completed();
            }
        });
        eventManager.register("after-sync-completed", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fireSync(new SimpleEvent());

        assertEquals(List.of("completed", "after"), order);
    }

    @Test
    void fireSyncRejectsSuspendingHandler() {
        eventManager.register("suspends", new EventListener() {
            @EventHandler
            public EventTask onSimple(SimpleEvent event) {
                return EventTask.withContinuation(c -> {});
            }
        });

        assertThrows(IllegalStateException.class, () -> eventManager.fireSync(new SimpleEvent()));
    }

    @Test
    void taskAsyncRunsOffTheCallingThread() {
        // EventTask.async(...) 声明必须换线程：不能 inline 跑在调用方线程上
        AtomicReference<String> taskThread = new AtomicReference<>();
        String callingThread = Thread.currentThread().getName();

        eventManager.register("task-async", new EventListener() {
            @EventHandler
            public EventTask onSimple(SimpleEvent event) {
                return EventTask.async(() -> taskThread.set(Thread.currentThread().getName()));
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertNotNull(taskThread.get());
        assertFalse(callingThread.equals(taskThread.get()),
                "EventTask.async 不应在调用方线程执行");
    }

    @Test
    void voidHandlerThatTakesContinuationStillCompletes() {
        List<String> order = new ArrayList<>();

        eventManager.register("void-with-continuation", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onSimple(SimpleEvent event, Continuation continuation) {
                order.add("void");
            }
        });
        eventManager.register("after-void", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("void", "after"), order);
    }

    @Test
    void voidHandlerMayResumeSynchronously() {
        List<String> order = new ArrayList<>();

        eventManager.register("resuming-void", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onSimple(SimpleEvent event, Continuation continuation) {
                continuation.resume();
                order.add("void");
            }
        });
        eventManager.register("after-resuming-void", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("void", "after"), order);
    }

    @Test
    void cancellingStopsPropagation() {
        List<String> order = new ArrayList<>();

        eventManager.register("cancelling", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onCancellable(CancellableEvent event) {
                order.add("cancelling");
                event.setCancelled(true);
            }
        });
        eventManager.register("skipped", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onCancellable(CancellableEvent event) {
                order.add("skipped");
            }
        });

        CancellableEvent event = new CancellableEvent();
        eventManager.fire(event).join();

        assertEquals(List.of("cancelling"), order);
        assertTrue(event.isCancelled());
    }

    @Test
    void acceptsCancelledHandlerStillRuns() {
        List<String> order = new ArrayList<>();

        eventManager.register("cancelling", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void onCancellable(CancellableEvent event) {
                event.setCancelled(true);
            }
        });
        eventManager.register("monitor", new EventListener() {
            @EventHandler(priority = EventPriority.MONITOR, acceptsCancelled = true)
            public void onCancellable(CancellableEvent event) {
                order.add("monitor");
            }
        });

        eventManager.fire(new CancellableEvent()).join();

        assertEquals(List.of("monitor"), order);
    }

    @Test
    void asyncHandlerRunsOffTheCallingThread() {
        AtomicReference<String> handlerThread = new AtomicReference<>();
        String callingThread = Thread.currentThread().getName();

        eventManager.register("blocking", new EventListener() {
            @EventHandler(async = true)
            public void onSimple(SimpleEvent event) {
                handlerThread.set(Thread.currentThread().getName());
            }
        });

        eventManager.fire(new SimpleEvent()).join();

        assertNotNull(handlerThread.get());
        assertFalse(callingThread.equals(handlerThread.get()),
                "声明 async 的处理器不应在调用方线程执行");
    }

    @Test
    void fireAndForgetRunsHandlers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        eventManager.register("notified", new EventListener() {
            @EventHandler
            public void onSimple(SimpleEvent event) {
                latch.countDown();
            }
        });

        eventManager.fireAndForget(new SimpleEvent());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void polymorphicHandlerReceivesSubtypes() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        eventManager.register("polymorphic", new EventListener() {
            @EventHandler(polymorphic = true)
            public void onBase(BaseEvent event) {
                invoked.set(true);
            }
        });

        eventManager.fire(new DerivedEvent()).join();

        assertTrue(invoked.get(), "polymorphic 处理器应收到子类型事件");
    }

    @Test
    void nonPolymorphicHandlerIgnoresSubtypes() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        eventManager.register("exact", new EventListener() {
            @EventHandler
            public void onBase(BaseEvent event) {
                invoked.set(true);
            }
        });

        eventManager.fire(new DerivedEvent()).join();

        assertFalse(invoked.get(), "非 polymorphic 处理器不应收到子类型事件");
    }

    @Test
    void exactHandlerReceivesItsOwnType() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        eventManager.register("exact-base", new EventListener() {
            @EventHandler
            public void onBase(BaseEvent event) {
                invoked.set(true);
            }
        });

        eventManager.fire(new BaseEvent()).join();

        assertTrue(invoked.get());
    }

    @Test
    void detachedAsyncHandlerStillAdvancesAfterResume() throws Exception {
        AtomicReference<Continuation> captured = new AtomicReference<>();
        CountDownLatch invoked = new CountDownLatch(1);
        List<String> order = new ArrayList<>();

        // 就地调用时该处理器会被提交到执行器，因此 invoke 的返回值无人接收；
        // 恢复必须仍能推进派发。
        eventManager.register("async-suspending", new EventListener() {
            @EventHandler(priority = EventPriority.HIGH, async = true)
            public EventTask onSimple(SimpleEvent event, Continuation continuation) {
                captured.set(continuation);
                invoked.countDown();
                return EventTask.withContinuation(c -> {});
            }
        });
        eventManager.register("after-detached", new EventListener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onSimple(SimpleEvent event) {
                order.add("after");
            }
        });

        CompletableFuture<SimpleEvent> future = eventManager.fire(new SimpleEvent());

        assertTrue(invoked.await(5, TimeUnit.SECONDS), "异步处理器未被调用");
        assertFalse(future.isDone());
        assertTrue(order.isEmpty());

        captured.get().resume();

        future.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("after"), order);
    }

    @Test
    void unregisteringRemovesHandlers() {
        List<String> order = new ArrayList<>();
        EventListener listener = new EventListener() {
            @EventHandler
            public void onSimple(SimpleEvent event) {
                order.add("ran");
            }
        };

        eventManager.register("removable", listener);
        eventManager.fire(new SimpleEvent()).join();
        eventManager.unregister("removable");
        eventManager.fire(new SimpleEvent()).join();

        assertEquals(List.of("ran"), order);
    }

    /** 无附加状态的事件，用于验证最简派发路径。 */
    static final class SimpleEvent implements IStarlightEvent {}

    /** 多态匹配测试用的父类型。 */
    static class BaseEvent implements IStarlightEvent {}

    /** {@link BaseEvent} 的子类型，用于验证多态匹配。 */
    static final class DerivedEvent extends BaseEvent {}

    /** 可取消事件，用于验证取消语义。 */
    static final class CancellableEvent implements ICancellableEvent {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled.set(cancelled);
        }
    }
}
