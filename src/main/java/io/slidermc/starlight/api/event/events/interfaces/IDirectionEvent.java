package io.slidermc.starlight.api.event.events.interfaces;

import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;

public interface IDirectionEvent extends IStarlightEvent {
    ProtocolDirection getDirection();
}
