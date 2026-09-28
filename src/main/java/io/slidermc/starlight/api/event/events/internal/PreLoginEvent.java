package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.network.context.ConnectionContext;
import net.kyori.adventure.text.Component;

/**
 * 登录前拦截事件，在收到 LoginStart 数据包后、加密/离线认证决策之前触发。
 *
 * <p>插件可通过此事件：
 * <ul>
 *   <li>{@link #deny(Component)} — 拒绝登录并踢出玩家</li>
 *   <li>{@link #forceOnlineMode()} — 强制此连接走正版验证（覆盖全局配置）</li>
 *   <li>{@link #forceOfflineMode()} — 强制此连接跳过 Mojang 验证（覆盖全局配置）</li>
 * </ul>
 *
 * <p>未调用任何上述方法时，连接将按全局配置正常处理。
 *
 * <p>需要异步决策（例如查询数据库或外部接口）时，处理器返回
 * {@link io.slidermc.starlight.api.event.EventTask} 并自行恢复派发；登录流程会一直等到事件
 * 定稿，因此处理器在恢复之前设置的 {@code deny}/{@code forceOnlineMode} 等状态一定生效。
 */
public class PreLoginEvent implements IStarlightEvent {

    public enum PreLoginResult {
        /** 按全局配置正常处理 */
        ALLOWED,
        /** 拒绝登录 */
        DENIED,
        /** 强制走正版验证 */
        FORCE_ONLINE,
        /** 强制跳过正版验证 */
        FORCE_OFFLINE
    }

    private final ConnectionContext connection;
    private final String username;
    private volatile PreLoginResult result = PreLoginResult.ALLOWED;
    private volatile Component denyReason;

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

    /**
     * 此连接是否被强制跳过 Mojang 验证。
     *
     * <p>用于无法完成正版验证的客户端（如通过 Geyser 接入的基岩版玩家）。
     *
     * <p>只影响验证，不影响加密：是否对连接加密由全局 {@code encryption} 配置单独决定，
     * 两者相互独立。
     *
     * @return 强制离线返回 true
     */
    public boolean isForceOfflineMode() {
        return result == PreLoginResult.FORCE_OFFLINE;
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
     * 强制此连接跳过 Mojang 验证，覆盖全局 {@code online-mode}。
     *
     * <p>档案将使用离线 UUID。是否加密不受此方法影响，由全局 {@code encryption} 决定。
     */
    public void forceOfflineMode() {
        this.result = PreLoginResult.FORCE_OFFLINE;
    }
}
