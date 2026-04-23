package io.slidermc.starlight.utils;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.ReceivePluginMessageEvent;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import net.kyori.adventure.key.Key;

import java.util.concurrent.CompletableFuture;

public class EventUtils {
    public static CompletableFuture<ReceivePluginMessageEvent> createPluginMessageEventAndAsyncFire(ProtocolDirection direction, Key key, byte[] data, StarlightProxy proxy) {
        ReceivePluginMessageEvent pluginMessageEvent = new ReceivePluginMessageEvent(direction, key, data);
        return proxy.getEventManager().fireAsync(pluginMessageEvent);
    }
}
