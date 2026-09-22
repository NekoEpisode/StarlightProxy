package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.context.ConnectionContext;

/**
 * 在玩家 GameProfile 确定之后、LoginSuccess 发送之前触发。
 *
 * <p>插件可通过此事件修改 GameProfile（例如替换 UUID 或移除皮肤 Properties）。
 * 取消事件将中止登录流程并踢出玩家。
 *
 * <p>此事件在以下场景触发：
 * <ul>
 *   <li>离线模式登录（GameProfile 基于离线 UUID 生成）</li>
 *   <li>加密但未验证的登录（离线 UUID）</li>
 *   <li>正版验证登录（Mojang 返回的 GameProfile）</li>
 * </ul>
 */
public class GameProfileRequestEvent implements ICancellableEvent {
    private volatile boolean cancelled;
    private final ConnectionContext connection;
    private volatile GameProfile gameProfile;
    private final boolean onlineMode;

    public GameProfileRequestEvent(ConnectionContext connection, GameProfile gameProfile, boolean onlineMode) {
        this.connection = connection;
        this.gameProfile = gameProfile;
        this.onlineMode = onlineMode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public ConnectionContext getConnection() {
        return connection;
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public void setGameProfile(GameProfile gameProfile) {
        this.gameProfile = gameProfile;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }
}
