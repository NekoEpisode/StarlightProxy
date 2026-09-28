package io.slidermc.starlight.api.event;

/**
 * 事件处理器暂停执行的凭据，用于在异步操作完成后通知事件系统继续派发。
 *
 * <p>事件处理器通过返回 {@link EventTask} 声明自己需要暂停。当处理器无法在当次调用中完成时
 * （例如需要等待网络请求、数据库查询），它应保存 {@code Continuation} 并在操作完成后调用
 * {@link #resume()}；事件系统会在此时从<b>下一个</b>处理器继续派发，保持处理器的执行顺序不变。
 *
 * <p>处理器不持有 {@code Continuation} 时，{@link EventTask#completed()} 表示同步完成。
 *
 * <p>典型用法：
 * <pre>{@code
 * @EventHandler
 * public EventTask onLogin(PlayerLoginEvent event, Continuation continuation) {
 *     someAsyncLookup(event.getPlayer().getUniqueId()).whenComplete((result, error) -> {
 *         if (error != null) {
 *             continuation.resumeWithException(error);
 *         } else {
 *             event.setSomething(result);
 *             continuation.resume();
 *         }
 *     });
 *     return EventTask.withContinuation(c -> {});
 * }
 * }</pre>
 *
 * <p><b>仅可恢复一次。</b>重复调用会抛出 {@link IllegalStateException}。
 *
 * <p><b>恢复时机决定后续处理器在哪个线程执行：</b>
 * <ul>
 *   <li>处理器<b>返回之前</b>恢复（无论在哪个线程）—— 视为同步完成，派发由原调用方线程继续，
 *       不产生额外线程调度。</li>
 *   <li>处理器<b>返回之后</b>恢复 —— 由恢复线程继续派发，其后的处理器都在该线程上执行。
 *       因此处理器不应假定自己运行在固定的线程上。</li>
 * </ul>
 */
public interface Continuation {

    /**
     * 恢复派发。
     *
     * <p>可以在任意线程调用。若在处理器尚未返回 {@link EventTask} 时同步调用，派发将继续在
     * 当前线程上进行，不产生额外的线程调度。
     *
     * @throws IllegalStateException 若此前已经恢复过
     */
    void resume();

    /**
     * 以异常恢复派发。
     *
     * <p>异常会被事件系统记录，并表现为事件处理器执行失败：该处理器之后的处理器仍然会被调用，
     * 事件的完成状态不受影响。
     *
     * @param exception 导致处理器无法正常完成的异常
     * @throws IllegalStateException 若此前已经恢复过
     */
    void resumeWithException(Throwable exception);
}
