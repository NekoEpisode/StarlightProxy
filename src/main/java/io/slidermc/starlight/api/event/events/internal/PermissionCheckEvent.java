package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;

public class PermissionCheckEvent implements ICancellableEvent, IPlayerEvent {

    private final ProxiedPlayer player;
    private final String permission;
    private volatile boolean cancelled = false;
    private volatile boolean result;

    public PermissionCheckEvent(ProxiedPlayer player, String permission, boolean result) {
        this.player = player;
        this.permission = permission;
        this.result = result;
    }

    @Override
    public ProxiedPlayer getPlayer() {
        return player;
    }

    public String getPermission() {
        return permission;
    }

    public boolean getResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
