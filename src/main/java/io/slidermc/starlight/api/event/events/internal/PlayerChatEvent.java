package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;

public class PlayerChatEvent implements ICancellableEvent, IPlayerEvent {
    private boolean cancelled = false;
    private final ProxiedPlayer player;

    private String message;

    public PlayerChatEvent(ProxiedPlayer player, String message) {
        this.player = player;
        this.message = message;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public ProxiedPlayer getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
