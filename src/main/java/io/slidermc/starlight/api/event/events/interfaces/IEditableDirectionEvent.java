package io.slidermc.starlight.api.event.events.interfaces;

import io.slidermc.starlight.network.protocolenum.ProtocolDirection;

public interface IEditableDirectionEvent extends IDirectionEvent {
    void setDirection(ProtocolDirection protocolDirection);
}
