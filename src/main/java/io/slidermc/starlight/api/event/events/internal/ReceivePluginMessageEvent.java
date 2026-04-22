package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.helper.PluginMessageResult;
import io.slidermc.starlight.api.event.events.interfaces.ICancellableEvent;
import io.slidermc.starlight.api.event.events.interfaces.IDirectionEvent;
import io.slidermc.starlight.api.event.events.interfaces.IResultableEvent;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import net.kyori.adventure.key.Key;

public class ReceivePluginMessageEvent implements IDirectionEvent, ICancellableEvent, IResultableEvent {
    private Key key;
    private byte[] data;

    private int result = PluginMessageResult.NONE.getCode();

    private final ProtocolDirection direction;

    public ReceivePluginMessageEvent(ProtocolDirection direction, Key key, byte[] data) {
        this.direction = direction;
        this.key = key;
        this.data = data;
    }

    @Override
    public boolean isCancelled() {
        return result == PluginMessageResult.DROPPED.getCode();
    }

    @Override
    public void setCancelled(boolean cancelled) {
        if (cancelled)
            result = PluginMessageResult.DROPPED.getCode();
        else
            result = PluginMessageResult.NONE.getCode();
    }

    @Override
    public ProtocolDirection getDirection() {
        return direction;
    }

    @Override
    public int getResult() {
        return result;
    }

    public PluginMessageResult getResultWithPluginMessageResult() {
        return PluginMessageResult.fromCode(result);
    }

    @Override
    public void setResult(int result) {
        this.result = result;
    }

    public void setResultWithPluginMessageResult(PluginMessageResult pluginMessageResult) {
        this.result = pluginMessageResult.getCode();
    }

    public Key getKey() {
        return key;
    }

    public byte[] getData() {
        return data;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
