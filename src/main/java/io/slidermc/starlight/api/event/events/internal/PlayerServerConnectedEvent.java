package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.server.ProxiedServer;

import java.util.Optional;

public class PlayerServerConnectedEvent implements IPlayerEvent {
    private final ProxiedPlayer player;
    private final ProxiedServer previous;
    private final ProxiedServer target;

    public PlayerServerConnectedEvent(ProxiedPlayer player, ProxiedServer previous, ProxiedServer target) {
        this.player = player;
        this.previous = previous;
        this.target = target;
    }

    @Override
    public ProxiedPlayer getPlayer() {
        return player;
    }

    public Optional<ProxiedServer> getPrevious() {
        return Optional.ofNullable(previous);
    }

    public Optional<ProxiedServer> getTarget() {
        return Optional.ofNullable(target);
    }
}
