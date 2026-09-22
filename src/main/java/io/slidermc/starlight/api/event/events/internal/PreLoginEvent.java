package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.network.context.ConnectionContext;
import net.kyori.adventure.text.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录前拦截事件，在收到 LoginStart 数据包后、加密/离线认证决策之前触发。
 *
 * <p>插件可通过此事件：
 * <ul>
 *   <li>{@link #deny(Component)} — 拒绝登录并踢出玩家</li>
 *   <li>{@link #forceOnlineMode()} — 强制此连接走正版验证（覆盖全局 offline-mode 配置）</li>
 *   <li>{@link #registerIntent(String)} / {@link #completeIntent(String)} — 注册异步意图，延迟登录流程直到所有意图完成</li>
 * </ul>
 *
 * <p>未调用任何上述方法时，连接将按全局配置正常处理。
 */
public class PreLoginEvent implements IStarlightEvent {

    public enum PreLoginResult {
        /** 按全局配置正常处理 */
        ALLOWED,
        /** 拒绝登录 */
        DENIED,
        /** 强制走正版验证 */
        FORCE_ONLINE
    }

    private final ConnectionContext connection;
    private final String username;
    private volatile PreLoginResult result = PreLoginResult.ALLOWED;
    private volatile Component denyReason;
    private final Set<String> intents = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> completionFuture = new CompletableFuture<>();

    public PreLoginEvent(ConnectionContext connection, String username) {
        this.connection = connection;
        this.username = username;
    }

    public ConnectionContext getConnection() {
        return connection;
    }

    public String getUsername() {
        return username;
    }

    public PreLoginResult getResult() {
        return result;
    }

    public void setResult(PreLoginResult result) {
        this.result = result;
    }

    public boolean isDenied() {
        return result == PreLoginResult.DENIED;
    }

    public boolean isForceOnlineMode() {
        return result == PreLoginResult.FORCE_ONLINE;
    }

    public Component getDenyReason() {
        return denyReason;
    }

    public void deny(Component reason) {
        this.result = PreLoginResult.DENIED;
        this.denyReason = reason;
    }

    public void forceOnlineMode() {
        this.result = PreLoginResult.FORCE_ONLINE;
    }

    /**
     * 注册一个异步意图，挂起登录流程。
     * 每个意图必须通过 {@link #completeIntent(String)} 来完成，
     * 当所有意图完成后登录流程自动继续。
     *
     * @param pluginId 插件标识符，建议使用插件 ID
     */
    public void registerIntent(String pluginId) {
        if (result != PreLoginResult.DENIED) {
            intents.add(pluginId);
        }
    }

    /**
     * 完成一个之前注册的异步意图。
     *
     * @param pluginId 与 {@link #registerIntent(String)} 相同的标识符
     */
    public void completeIntent(String pluginId) {
        intents.remove(pluginId);
        if (intents.isEmpty()) {
            completionFuture.complete(null);
        }
    }

    public boolean hasIntents() {
        return !intents.isEmpty();
    }

    public CompletableFuture<Void> getCompletionFuture() {
        return completionFuture;
    }

    /**
     * 当没有注册任何意图时自动完成，避免登录流程永久挂起。
     * 在所有同步处理器执行完毕后由内部调用。
     */
    public void tryComplete() {
        if (intents.isEmpty()) {
            completionFuture.complete(null);
        }
    }
}
