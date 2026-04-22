package io.slidermc.starlight.eventlisteners;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.slidermc.starlight.api.event.EventHandler;
import io.slidermc.starlight.api.event.EventListener;
import io.slidermc.starlight.api.event.EventPriority;
import io.slidermc.starlight.api.event.events.helper.PluginMessageResult;
import io.slidermc.starlight.api.event.events.internal.ReceivePluginMessageEvent;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginMessageEventListener implements EventListener {
    private static final Key BRAND = Key.key("minecraft:brand");
    private static final Logger log = LoggerFactory.getLogger(PluginMessageEventListener.class);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPluginMessage(ReceivePluginMessageEvent event) {
        if (event.getDirection() == ProtocolDirection.CLIENTBOUND) { // 往客户端方向
            if (BRAND.equals(event.getKey())) {
                log.debug("收到服务器发给客户端的brand");
                // 是服务器发给客户端的brand包
                ByteBuf byteBuf = Unpooled.buffer();
                byteBuf.writeBytes(event.getData());
                String brand = MinecraftCodecUtils.readString(byteBuf);
                brand = "Starlight -> " + brand; // 拼接新brand
                byteBuf.release();
                byteBuf = Unpooled.buffer();
                MinecraftCodecUtils.writeString(byteBuf, brand); // 写入新brand
                byte[] newData = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(newData);
                event.setData(newData); // 修改brand
                event.setResultWithPluginMessageResult(PluginMessageResult.FORWARD);
                byteBuf.release();
            }
        } else if (event.getDirection() == ProtocolDirection.SERVERBOUND) { // 往服务端方向
            if (BRAND.equals(event.getKey())) {
                ByteBuf byteBuf = Unpooled.buffer();
                byteBuf.writeBytes(event.getData());
                String brand = MinecraftCodecUtils.readString(byteBuf);
                log.debug("收到客户端发给服务器的brand: {}", brand);
                byteBuf.release();
            }
        }
    }
}
