package io.slidermc.starlight.network.packet.packets.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClientboundPluginMessageConfigurationPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundPluginMessageConfigurationPacket.class);
    private Key key;
    private byte[] data;

    public ClientboundPluginMessageConfigurationPacket() {}

    public ClientboundPluginMessageConfigurationPacket(Key key, byte[] data) {
        this.key = key;
        this.data = data;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeKey(byteBuf, key);
        byteBuf.writeBytes(data);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.key = MinecraftCodecUtils.readKey(byteBuf);
        this.data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(this.data);
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public static class Listener implements IPacketListener<ClientboundPluginMessageConfigurationPacket> {
        private static final Key BRAND_KEY = Key.key("minecraft:brand");

        @Override
        public void handle(ClientboundPluginMessageConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            StarlightMinecraftClient client = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient();

            if (BRAND_KEY.equals(packet.getKey())) {
                // 读取原始 brand（data 内容为 VarInt 长度前缀 + UTF-8 字节）
                ByteBuf in = Unpooled.wrappedBuffer(packet.getData());
                String brand = MinecraftCodecUtils.readString(in);
                in.release();

                // 修改 brand
                brand = "Starlight -> " + brand;
                log.debug("修改brand为: {}", brand);

                // 写回：用临时 buffer，自动扩容，无需预知大小
                ByteBuf tmp = Unpooled.buffer(1);
                MinecraftCodecUtils.writeString(tmp, brand);
                byte[] newData = new byte[tmp.readableBytes()];
                tmp.readBytes(newData);
                tmp.release();

                packet.setData(newData);
            }

            // 转发给玩家（无论是否修改过）
            Channel playerChannel = client.getPlayerChannel();
            if (playerChannel != null && playerChannel.isActive()) {
                playerChannel.writeAndFlush(packet);
            }
        }
    }
}
