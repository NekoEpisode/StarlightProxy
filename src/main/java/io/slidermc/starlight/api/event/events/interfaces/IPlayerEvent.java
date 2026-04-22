package io.slidermc.starlight.api.event.events.interfaces;

import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;

public interface IPlayerEvent extends IStarlightEvent {
    ProxiedPlayer getPlayer();
}
