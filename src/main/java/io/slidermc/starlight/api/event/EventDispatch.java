package io.slidermc.starlight.api.event;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次事件派发的执行状态。
 *
 * <p>派发串行执行处理器；处理器可以暂停派发，并在任意线程通过 {@link Continuation#resume()}
 * 让派发从下一个处理器继续。暂停即返回、恢复即续跑，过程中不阻塞任何线程。
 *
 * <p>取消语义：事件被取消后派发立即终止；声明了 {@link EventHandler#acceptsCancelled()}
 * 的处理器不受影响。
 *
 * <p>本类不对外暴露。插件通过 {@link EventManager#fire(IStarlightEvent)} 返回的 future
 * 获得"事件已定稿"的信号。
 */
final class EventDispatch {

    private static final Logger log = LoggerFactory.getLogger(EventDispatch.class);

    private EventDispatch() {
    }

    /**
     * 派发一个事件。
     *
     * <p>若存在需要换线程的处理器，整个派发被提交到事件执行器（虚拟线程），以免阻塞调用方线程；
     * 否则派发就在调用方线程上开始。
     *
     * @param manager  事件管理器，提供日志与执行器
     * @param event    要派发的事件
     * @param handlers 命中的处理器，已按优先级降序排列
     * @param future   完成信号；为 {@code null} 表示调用方不关心结果
     * @param <E>      事件类型
     */
    static <E extends IStarlightEvent> void dispatch(
            final EventManager manager,
            final E event,
            final List<EventManager.Invocation> handlers,
            final CompletableFuture<E> future
    ) {
        if (handlers == null || handlers.isEmpty()) {
            complete(future, event);
            return;
        }

        boolean needsAsync = false;
        for (EventManager.Invocation handler : handlers) {
            if (handler.requiresAsync()) {
                needsAsync = true;
                break;
            }
        }

        Executor executor = manager.eventExecutor();
        if (needsAsync) {
            // 存在阻塞型处理器：整个派发在执行器上开始，避免占用调用方线程
            executor.execute(() -> iterate(executor, event, handlers, future, 0, true));
        } else {
            iterate(executor, event, handlers, future, 0, false);
        }
    }

    /**
     * 同步派发一个事件，并在任何处理器试图暂停时立即失败。
     *
     * <p>用于无法改为异步的调用点。处理器一旦暂停，本方法抛出 {@link IllegalStateException}：
     * 与其让调用方（很可能是 Netty 事件循环线程）永久阻塞，不如显式暴露该处理器与调用点不兼容。
     *
     * @param manager  事件管理器
     * @param event    要派发的事件
     * @param handlers 命中的处理器
     * @param <E>      事件类型
     * @throws IllegalStateException 若有处理器暂停了派发
     */
    static <E extends IStarlightEvent> void dispatchSync(
            final EventManager manager,
            final E event,
            final List<EventManager.Invocation> handlers
    ) {
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        Executor executor = manager.eventExecutor();
        if (iterateSync(executor, event, handlers)) {
            throw new IllegalStateException("Event handler suspended the dispatch of "
                    + event.getClass().getSimpleName()
                    + ", which cannot be awaited synchronously. Use EventManager#fire instead.");
        }
    }

    /**
     * 同步执行处理器，遇到暂停即报告失败。
     *
     * @param executor 事件执行器
     * @param event    事件实例
     * @param handlers 命中的处理器
     * @param <E>      事件类型
     * @return 有处理器暂停时返回 {@code true}
     */
    private static <E extends IStarlightEvent> boolean iterateSync(
            final Executor executor,
            final E event,
            final List<EventManager.Invocation> handlers
    ) {
        for (EventManager.Invocation handler : handlers) {
            if (!handler.acceptsCancelled() && isCancelled(event)) {
                return false;
            }
            if (handler.canSuspend()) {
                // 能暂停的处理器无法同步等待：它可能在返回之后才恢复
                return true;
            }

            try {
                handler.complete(event, UNSUSPENDABLE);
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                log.error("Event handler threw for {}", event.getClass().getSimpleName(), t);
            }
        }
        return false;
    }

    /**
     * 供同步派发使用的占位凭据：同步路径不提供暂停能力，误用会立即失败。
     */
    private static final Continuation UNSUSPENDABLE = new Continuation() {
        @Override
        public void resume() {
            throw new IllegalStateException("Synchronous event dispatch does not support suspending");
        }

        @Override
        public void resumeWithException(final Throwable exception) {
            throw new IllegalStateException("Synchronous event dispatch does not support suspending",
                    exception);
        }
    };

    /**
     * 从 {@code offset} 开始串行执行处理器，遇到暂停即返回。
     *
     * @param executor       事件执行器，用于运行阻塞型处理器
     * @param event          事件实例
     * @param handlers       命中的处理器
     * @param future         完成信号；可为 {@code null}
     * @param offset         起始下标
     * @param currentlyAsync 是否已处于事件执行器线程上
     * @param <E>            事件类型
     */
    private static <E extends IStarlightEvent> void iterate(
            final Executor executor,
            final E event,
            final List<EventManager.Invocation> handlers,
            final CompletableFuture<E> future,
            final int offset,
            final boolean currentlyAsync
    ) {
        for (int index = offset; index < handlers.size(); index++) {
            EventManager.Invocation handler = handlers.get(index);

            if (!handler.acceptsCancelled() && isCancelled(event)) {
                // 事件已被取消，终止传播。必须完成 future，否则等待方会永久挂起。
                break;
            }

            Continue pause = new Continue(executor, event, handlers, future,
                    index, currentlyAsync);

            if (!currentlyAsync && handler.requiresAsync()) {
                // 阻塞型处理器不得占用调用方线程，移交事件执行器。
                // 此处无接收者处理返回值，因此由 invoke 自行在"同步恢复"时续跑派发。
                executor.execute(() -> pause.invoke(true));
                return;
            }

            if (pause.invoke(false)) {
                // 派发由 Continue 在恢复之后继续
                return;
            }
        }

        complete(future, event);
    }

    /**
     * 以事件实例完成派发信号。
     *
     * <p>所有终止路径（正常跑完、事件被取消）都必须经过此处，否则等待该事件的调用方
     * 会永久挂起。
     *
     * @param future 完成信号；为 {@code null} 时无需处理
     * @param event  事件实例
     * @param <E>    事件类型
     */
    private static <E extends IStarlightEvent> void complete(final CompletableFuture<E> future,
                                                             final E event) {
        if (future != null) {
            future.complete(event);
        }
    }

    private static boolean isCancelled(final IStarlightEvent event) {
        return event instanceof ICancellableEvent cancellable && cancellable.isCancelled();
    }

    /**
     * 单个处理器的暂停状态。
     *
     * <p>{@link #state} 是唯一的协调依据，它同时表达了"处理器是否仍在执行"与"派发继续权归谁"：
     * <table border="1">
     *   <caption>状态转移</caption>
     *   <tr><th>情形</th><th>转移</th><th>谁推进派发</th></tr>
     *   <tr>
     *     <td>处理器返回前恢复</td>
     *     <td>{@code EXECUTING → SIGNALLED}</td>
     *     <td>{@link #invoke(boolean)} 返回 {@code false}，调用方线程继续循环（无线程调度）</td>
     *   </tr>
     *   <tr>
     *     <td>处理器返回后恢复</td>
     *     <td>{@code SUSPENDED → SIGNALLED}</td>
     *     <td>恢复线程调用 {@link #proceed()}</td>
     *   </tr>
     *   <tr>
     *     <td>处理器返回时仍未恢复</td>
     *     <td>{@code EXECUTING → SUSPENDED}</td>
     *     <td>{@link #invoke(boolean)} 返回 {@code true}，由后续 {@code resume()} 推进</td>
     *   </tr>
     * </table>
     *
     * <p>三条转移由 CAS 保证互斥，因此后续处理器只会被执行一次。
     */
    private static final class Continue implements Continuation {

        /** 处理器正在执行中。 */
        private static final int STATE_EXECUTING = 0;
        /** 处理器已返回，等待外部恢复。 */
        private static final int STATE_SUSPENDED = 1;
        /** 处理器已完成，派发应继续。 */
        private static final int STATE_SIGNALLED = 2;

        private final Executor executor;
        private final IStarlightEvent event;
        private final List<EventManager.Invocation> handlers;
        private final CompletableFuture<? extends IStarlightEvent> future;
        private final int index;
        private final boolean currentlyAsync;

        private final AtomicInteger state = new AtomicInteger(STATE_EXECUTING);
        private final AtomicBoolean resumed = new AtomicBoolean(false);

        private Continue(
                final Executor executor,
                final IStarlightEvent event,
                final List<EventManager.Invocation> handlers,
                final CompletableFuture<? extends IStarlightEvent> future,
                final int index,
                final boolean currentlyAsync
        ) {
            this.executor = executor;
            this.event = event;
            this.handlers = handlers;
            this.future = future;
            this.index = index;
            this.currentlyAsync = currentlyAsync;
        }

        /**
         * 调用处理器并判定派发是否暂停。
         *
         * <p>处理器抛出的异常在此就地记录，不影响派发继续。
         *
         * @param detached 本次调用是否运行在无人接收返回值的位置（例如已提交给执行器）。
         *                 为 {@code true} 时，若处理器在返回前自行恢复，需要由本方法续跑派发，
         *                 否则派发会失去推进者。
         * @return 派发需要等待 {@link #resume()} 时返回 {@code true}
         */
        boolean invoke(final boolean detached) {
            try {
                handlers.get(index).complete(event, this);
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                log.error("Event handler threw for {}", event.getClass().getSimpleName(), t);
            }

            if (!handlers.get(index).canSuspend()) {
                // 处理器无法暂停：方法返回即处理完毕
                return false;
            }

            if (state.compareAndSet(STATE_EXECUTING, STATE_SUSPENDED)) {
                // 处理器返回时未恢复，派发暂停，等待 resume()
                return true;
            }

            // 状态已是 SIGNALLED：处理器在返回前已自行恢复
            if (detached) {
                // 提交给执行器时无接收者处理返回值，由此处续跑
                proceed();
            }
            return false;
        }

        @Override
        public void resume() {
            if (resumed.getAndSet(true)) {
                throw new IllegalStateException("Continuation resumed more than once");
            }
            // 恢复发生在处理器返回之前：置为 SIGNALLED，由 invoke() 判定后继续循环
            if (state.compareAndSet(STATE_EXECUTING, STATE_SIGNALLED)) {
                return;
            }
            // 恢复发生在处理器返回之后：取得继续权，由本线程续跑
            if (state.compareAndSet(STATE_SUSPENDED, STATE_SIGNALLED)) {
                proceed();
            }
        }

        @Override
        public void resumeWithException(final Throwable exception) {
            log.error("Event handler failed for {}", event.getClass().getSimpleName(), exception);
            resume();
        }

        private void proceed() {
            iterate(executor, castEvent(), handlers, castFuture(), index + 1, currentlyAsync);
        }

        @SuppressWarnings("unchecked")
        private <E extends IStarlightEvent> E castEvent() {
            return (E) event;
        }

        @SuppressWarnings("unchecked")
        private <E extends IStarlightEvent> CompletableFuture<E> castFuture() {
            return (CompletableFuture<E>) future;
        }
    }
}
