package io.slidermc.starlight.api.event.events.interfaces;

import io.slidermc.starlight.api.event.IStarlightEvent;

public interface ICancellableEvent extends IStarlightEvent {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}

