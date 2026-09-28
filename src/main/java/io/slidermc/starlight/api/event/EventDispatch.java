package io.slidermc.starlight.api.event;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
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
            // 存在声明 async 的处理器：整个派发在执行器上开始，避免占用调用方线程。
            // 处理器返回的任务若自行声明 requiresAsync()（见 EventTask#async），
            // 由 iterate 在到达该处理器时单独移交执行器。
            executor.execute(() -> iterate(executor, event, handlers, future, 0, true, manager));
        } else {
            iterate(executor, event, handlers, future, 0, false, manager);
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
        if (iterateSync(executor, event, handlers, manager)) {
            throw new IllegalStateException("Event handler suspended the dispatch of "
                    + event.getClass().getSimpleName()
                    + ", which cannot be awaited synchronously. Use EventManager#fire instead.");
        }
    }

    /**
     * 记录事件处理器抛出的异常。
     *
     * <p>控制台在接管 log4j2 输出后会重写多行内容，异常堆栈有可能在重写过程中丢失，
     * 导致排查时只看到一行消息、无从定位。因此这里额外把堆栈直接写到 {@code stderr}，
     * 不经由日志系统——堆栈对本项目排查问题过于关键，不能依赖日志管线的正确性。
     *
     * @param manager 事件管理器，用于取得翻译
     * @param handler 抛出异常的处理器
     * @param event   正在派发的事件
     * @param t       抛出的异常
     */
    private static void logHandlerFailure(final EventManager manager,
                                          final EventManager.Invocation handler,
                                          final IStarlightEvent event,
                                          final Throwable t) {
        String eventName = event.getClass().getSimpleName();
        log.error(manager.translateManager()
                        .translate("starlight.logging.error.event.handler_threw"),
                handler.describe(), eventName, t);

        PrintWriter fallback = new PrintWriter(System.err, true);
        fallback.println("[event] " + handler.describe() + " threw while handling " + eventName);
        t.printStackTrace(fallback);
    }

    /**
     * 同步执行处理器，遇到"返回后才能恢复"的情况即报告失败。
     *
     * <p>判定依据是处理器<b>是否真的挂起</b>，而不是它是否声明了暂停能力：
     * 返回 {@link EventTask#completed()} 或在执行期间同步恢复的处理器都必须能正常执行。
     *
     * @param executor 事件执行器
     * @param event    事件实例
     * @param handlers 命中的处理器
     * @param <E>      事件类型
     * @return 存在无法同步等待的处理器时返回 {@code true}
     */
    private static <E extends IStarlightEvent> boolean iterateSync(
            final Executor executor,
            final E event,
            final List<EventManager.Invocation> handlers,
            final EventManager manager
    ) {
        for (EventManager.Invocation handler : handlers) {
            if (!handler.acceptsCancelled() && isCancelled(event)) {
                return false;
            }

            SuspensionDetector detector = new SuspensionDetector(manager, handler, event);

            EventTask task;
            try {
                task = handler.complete(event, detector, manager.translateManager());
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                if (detector.afterExecute()) {
                    return true;
                }
                logHandlerFailure(manager, handler, event, t);
                continue;
            }

            if (task == null || EventTask.isCompleted(task)) {
                // 处理器已同步完成
                detector.taskReturned();
                continue;
            }

            try {
                task.execute(detector);
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                detector.taskReturned();
                if (detector.afterExecute()) {
                    return true;
                }
                log.error(manager.translateManager()
                                .translate("starlight.logging.error.event.dispatch_task_failed"),
                        event.getClass().getSimpleName(), handler.describe(), t);
                continue;
            }

            detector.taskReturned();
            if (!detector.completedSynchronously()) {
                // 任务返回后才恢复：同步派发无法安全等待
                return true;
            }
        }
        return false;
    }

    /**
     * 同步派发中的完成探测器。
     *
     * <p>区分两种恢复时机：
     * <ul>
     *   <li><b>任务执行期间</b>——属于同步完成，允许</li>
     *   <li><b>任务返回之后</b>——属于真正挂起，同步派发无从等待，记录下来交给调用方失败</li>
     * </ul>
     */
    private static final class SuspensionDetector implements Continuation {

        private final EventManager manager;
        private final EventManager.Invocation handler;
        private final IStarlightEvent event;

        private final AtomicBoolean executing = new AtomicBoolean(true);
        private final AtomicBoolean resumedDuringExecute = new AtomicBoolean(false);
        private final AtomicBoolean resumedAfterExecute = new AtomicBoolean(false);

        SuspensionDetector(final EventManager manager,
                           final EventManager.Invocation handler,
                           final IStarlightEvent event) {
            this.manager = manager;
            this.handler = handler;
            this.event = event;
        }

        @Override
        public void resume() {
            if (executing.get()) {
                resumedDuringExecute.set(true);
                return;
            }
            resumedAfterExecute.set(true);
        }

        @Override
        public void resumeWithException(final Throwable exception) {
            // 同步派发无法等待恢复，但异常本身仍需可见，否则处理器里的失败会被静默吞掉
            log.error(manager.translateManager()
                            .translate("starlight.logging.error.event.dispatch_task_failed"),
                    event.getClass().getSimpleName(), handler.describe(), exception);
            resume();
        }

        /** 标记任务已返回；此后的恢复都属于挂起。 */
        void taskReturned() {
            executing.set(false);
        }

        /**
         * @return 处理器在执行期间同步恢复过，属于同步完成
         */
        boolean completedSynchronously() {
            return resumedDuringExecute.get();
        }

        /**
         * @return 恢复请求发生在任务返回之后
         */
        boolean afterExecute() {
            return resumedAfterExecute.get();
        }
    }

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
            final boolean currentlyAsync,
            final EventManager manager
    ) {
        for (int index = offset; index < handlers.size(); index++) {
            EventManager.Invocation handler = handlers.get(index);

            if (!handler.acceptsCancelled() && isCancelled(event)) {
                // 事件已被取消，终止传播。必须完成 future，否则等待方会永久挂起。
                break;
            }

            Continue pause = new Continue(executor, manager, event, handlers, future,
                    index, currentlyAsync);

            EventTask task;
            try {
                task = handler.complete(event, pause, manager.translateManager());
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                logHandlerFailure(manager, handler, event, t);
                // 异常退出：不等待恢复，直接推进
                continue;
            }

            if (task == null || EventTask.isCompleted(task)) {
                // 处理器已同步完成（或返回了明确的空任务）
                continue;
            }

            if (!currentlyAsync && (handler.requiresAsync() || task.requiresAsync())) {
                // 需要换线程：不能占用调用方线程（调用方通常是 Netty 事件循环）。
                // 复用同一个 Continue（处理器可能已持有它），仅切换续跑时的线程语义。
                pause.markAsync();
                executor.execute(() -> pause.invoke(task, true));
                return;
            }

            if (pause.invoke(task, false)) {
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
     *     <td>{@link #invoke(EventTask, boolean)} 返回 {@code false}，调用方线程继续循环（无线程调度）</td>
     *   </tr>
     *   <tr>
     *     <td>处理器返回后恢复</td>
     *     <td>{@code SUSPENDED → SIGNALLED}</td>
     *     <td>恢复线程调用 {@link #proceed()}</td>
     *   </tr>
     *   <tr>
     *     <td>处理器返回时仍未恢复</td>
     *     <td>{@code EXECUTING → SUSPENDED}</td>
     *     <td>{@link #invoke(EventTask, boolean)} 返回 {@code true}，由后续 {@code resume()} 推进</td>
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
        private final EventManager manager;
        private final IStarlightEvent event;
        private final List<EventManager.Invocation> handlers;
        private final CompletableFuture<? extends IStarlightEvent> future;
        private final int index;
        private volatile boolean currentlyAsync;

        private final AtomicInteger state = new AtomicInteger(STATE_EXECUTING);
        private final AtomicBoolean resumed = new AtomicBoolean(false);

        private Continue(
                final Executor executor,
                final EventManager manager,
                final IStarlightEvent event,
                final List<EventManager.Invocation> handlers,
                final CompletableFuture<? extends IStarlightEvent> future,
                final int index,
                final boolean currentlyAsync
        ) {
            this.executor = executor;
            this.manager = manager;
            this.event = event;
            this.handlers = handlers;
            this.future = future;
            this.index = index;
            this.currentlyAsync = currentlyAsync;
        }

        /**
         * 标记后续派发将在事件执行器上进行。
         *
         * <p>用于"处理器运行在调用方线程、但其任务移交执行器"的情形：该处理器之后的派发
         * 都在执行器上继续，因此续跑时不应再尝试移交。
         */
        void markAsync() {
            this.currentlyAsync = true;
        }

        /**
         * 执行处理器返回的任务，并判定派发是否暂停。
         *
         * <p>任务抛出的异常在此就地记录，并按"不暂停"处理，不影响派发继续。
         *
         * @param task     处理器返回的任务；调用方需保证非 {@code null}
         * @param detached 本次调用是否运行在无人接收返回值的位置（例如已提交给执行器）。
         *                 为 {@code true} 时，若任务在返回前自行恢复，需要由本方法续跑派发，
         *                 否则派发会失去推进者。
         * @return 派发需要等待 {@link #resume()} 时返回 {@code true}
         */
        boolean invoke(final EventTask task, final boolean detached) {
            boolean taskFailed = false;
            try {
                task.execute(this);
            } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                log.error(manager.translateManager()
                                .translate("starlight.logging.error.event.dispatch_task_failed"),
                        event.getClass().getSimpleName(), handlers.get(index).describe(), t);
                taskFailed = true;
            }

            // 任务异常退出时它已无法再恢复，必须结束本次暂停，否则派发永久停滞
            if (!taskFailed && state.compareAndSet(STATE_EXECUTING, STATE_SUSPENDED)) {
                // 任务返回时未恢复，派发暂停，等待 resume()
                return true;
            }

            // 走到这里有两种情况：
            //   - 状态已是 SIGNALLED：任务已在返回前自行恢复
            //   - taskFailed：任务异常退出，不等待恢复
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
            logHandlerFailure(manager, handlers.get(index), event, exception);
            resume();
        }

        private void proceed() {
            iterate(executor, castEvent(), handlers, castFuture(), index + 1, currentlyAsync, manager);
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
