package io.slidermc.starlight.utils;

/**
 * 用于隔离"插件代码抛出的异常"与"JVM 级致命错误"的工具。
 *
 * <p>代理在调用插件代码（命令实现、事件监听器、生命周期回调）时需要捕获
 * {@link Throwable}：插件常因类加载失败抛出 {@link NoClassDefFoundError}、因二进制不兼容
 * 抛出 {@link NoSuchMethodError} 这类 {@link Error}，若只捕获 {@link Exception}，
 * 这些错误会穿透并终止整个代理。
 *
 * <p>但 {@code Throwable} 里也包含**不可恢复**的 JVM 级故障。把它们当成插件故障吞掉，
 * 会让代理在状态已损坏的情况下继续运行，掩盖真正的崩溃原因。因此调用方应写成：
 *
 * <pre>{@code
 * try {
 *     plugin.doSomething();
 * } catch (Throwable e) {
 *     ExceptionUtils.rethrowIfFatal(e);
 *     log.error("plugin failed", e);
 * }
 * }</pre>
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * 若给定对象是 JVM 级致命错误则原样抛出，否则立即返回。
     *
     * <p>被视为致命的类型：
     * <ul>
     *   <li>{@link VirtualMachineError} —— 包含 {@link OutOfMemoryError}、
     *       {@link StackOverflowError}、{@link InternalError} 等，JVM 已无法保证继续正确运行</li>
     * </ul>
     *
     * <p>与之相对，{@link LinkageError}（{@code NoClassDefFoundError}、{@code NoSuchMethodError}、
     * {@code ExceptionInInitializerError} 等）**不算致命**：它通常只是某个插件自身或其依赖的
     * 二进制问题，应当被隔离并记录，不影响代理与其它插件。
     *
     * @param throwable 待检查的异常或错误，可为 {@code null}
     */
    public static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError) {
            throw (Error) throwable;
        }
    }
}
