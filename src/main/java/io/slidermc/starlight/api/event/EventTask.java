package io.slidermc.starlight.api.event;


import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 事件处理器返回的执行描述，用于告知事件系统本次处理是否需要暂停、是否必须换线程执行。
 *
 * <p>事件系统以纯同步方式调用处理器；处理器返回本对象后，由事件系统决定后续行为：
 * <ul>
 *   <li>返回 {@link #completed()} —— 本次处理已结束，立即调用下一个处理器</li>
 *   <li>返回携带 {@link Continuation} 的任务 —— 暂停派发，等待处理器异步恢复</li>
 *   <li>{@link #requiresAsync()} 为 {@code true} —— 在事件执行器（虚拟线程）上运行，不占用调用方线程</li>
 * </ul>
 *
 * @see Continuation
 */
@FunctionalInterface
public interface EventTask {

    /**
     * 执行本次处理。
     *
     * <p>实现可以直接完成（不调用 {@code continuation}），也可以把 {@code continuation} 保存下来
     * 供异步回调使用。
     *
     * @param continuation 用于恢复派发的凭据
     */
    void execute(Continuation continuation);

    /**
     * 本次处理是否必须在其他线程上执行。
     *
     * <p>默认 {@code false}，表示事件系统可以在调用方线程上直接执行。若处理器会进行阻塞式
     * 调用，应返回 {@code true}，以免阻塞事件派发的调用方线程。
     *
     * @return 需要换线程时返回 {@code true}
     */
    default boolean requiresAsync() {
        return false;
    }

    /**
     * 一个已完成的空任务。适用于同步处理器。
     *
     * <p>返回后立即恢复派发，因此处理器不会真正暂停。
     *
     * @return 表示无需等待的任务
     */
    static EventTask completed() {
        return Continuation::resume;
    }

    /**
     * 一个立即执行并在完成后恢复的任务。
     *
     * <p>无论任务是否抛出异常，派发都会恢复：异常会以
     * {@link Continuation#resumeWithException(Throwable)} 的形式上报，而不是让派发永久挂起。
     *
     * @param task 要执行的动作
     * @return 执行 {@code task} 后恢复的任务
     */
    static EventTask of(final Runnable task) {
        Objects.requireNonNull(task, "task");
        return continuation -> {
            try {
                task.run();
            } catch (Throwable t) {
                continuation.resumeWithException(t);
                return;
            }
            continuation.resume();
        };
    }

    /**
     * 一个在事件执行器（虚拟线程）上运行的任务。
     *
     * <p>适用于会阻塞的处理器。无论任务是否抛出异常，派发都会恢复。
     *
     * @param task 要执行的动作
     * @return 强制异步执行的任务
     */
    static EventTask async(final Runnable task) {
        Objects.requireNonNull(task, "task");
        return new EventTask() {
            @Override
            public void execute(final Continuation continuation) {
                try {
                    task.run();
                } catch (Throwable t) {
                    continuation.resumeWithException(t);
                    return;
                }
                continuation.resume();
            }

            @Override
            public boolean requiresAsync() {
                return true;
            }
        };
    }

    /**
     * 一个由调用方自行控制恢复时机的任务。
     *
     * @param task 接收 {@link Continuation} 的动作
     * @return 由 {@code task} 决定何时恢复的任务
     */
    static EventTask withContinuation(final Consumer<Continuation> task) {
        Objects.requireNonNull(task, "task");
        return task::accept;
    }

    /**
     * 一个等待给定 {@link CompletableFuture 异步结果} 的任务。
     *
     * <p>结果正常完成时恢复派发；异常完成时以该异常恢复。
     *
     * @param future 需要等待的异步结果
     * @return 在 {@code future} 完成后恢复的任务
     */
    static EventTask resumeWhenComplete(final CompletableFuture<?> future) {
        Objects.requireNonNull(future, "future");
        return continuation -> future.whenComplete((result, error) -> {
            if (error != null) {
                continuation.resumeWithException(error);
            } else {
                continuation.resume();
            }
        });
    }
}
