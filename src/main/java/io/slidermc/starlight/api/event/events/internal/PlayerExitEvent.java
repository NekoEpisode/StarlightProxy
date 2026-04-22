package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;

public record PlayerExitEvent(ProxiedPlayer getPlayer) implements IPlayerEvent {
}