package io.slidermc.starlight.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 代理全局 Executor 管理器。
 * <p>统一管理各业务线程池，避免使用公共 ForkJoinPool 产生调度竞争。
 * 在代理关闭时应调用 {@link #shutdown()} 以优雅释放线程。
 */
public class ProxyExecutors {

    /**
     * 命令执行线程池。
     * 使用虚拟线程（Project Loom），每个命令调度开销极低，且不占用平台线程。
     */
    private final ExecutorService commandExecutor =
            Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("command-exec-", 0).factory()
            );

    /**
     * 事件分发线程池，使用虚拟线程并为每个事件派发任务创建独立的虚拟线程。
     * 建议通过 {@link io.slidermc.starlight.StarlightProxy#getExecutors()} 获取并
     * 在调用 {@code EventManager.fireAsync(event, executor)} 时传入。
     */
    private final ExecutorService eventExecutor =
            Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("event-exec-", 0).factory()
            );

    /**
     * 关闭所有线程池，应在代理停止时调用。
     */
    public void shutdown() {
        commandExecutor.shutdown();
        eventExecutor.shutdown();
    }

    public ExecutorService getCommandExecutor() {
        return commandExecutor;
    }

    /**
     * 返回事件处理专用的 ExecutorService（虚拟线程）。
     */
    public ExecutorService getEventExecutor() {
        return eventExecutor;
    }
}

