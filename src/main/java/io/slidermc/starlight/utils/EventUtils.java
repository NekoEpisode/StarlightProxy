package io.slidermc.starlight.utils;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.ReceivePluginMessageEvent;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import net.kyori.adventure.key.Key;

import java.util.concurrent.CompletableFuture;

public class EventUtils {
    public static CompletableFuture<ReceivePluginMessageEvent> createPluginMessageEventAndAsyncFire(ProtocolDirection direction,
                                                                                                    Key key, byte[] data, StarlightProxy proxy, Channel channel) {
        ReceivePluginMessageEvent pluginMessageEvent = new ReceivePluginMessageEvent(direction, key, data, channel);
        return proxy.getEventManager().fire(pluginMessageEvent);
    }
}
