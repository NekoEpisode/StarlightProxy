package io.slidermc.starlight.api.event.events.interfaces;

import io.slidermc.starlight.api.event.IStarlightEvent;

public interface IResultfulEvent extends IStarlightEvent {
    int getResult();
    void setResult(int result);
}
