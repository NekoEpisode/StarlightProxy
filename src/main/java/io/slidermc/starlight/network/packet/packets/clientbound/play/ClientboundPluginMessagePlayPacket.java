package io.slidermc.starlight.network.packet.packets.clientbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.helper.PluginMessageResult;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.EventUtils;
import net.kyori.adventure.key.Key;

public class ClientboundPluginMessagePlayPacket implements IMinecraftPacket {
    private Key key;
    private byte[] data;

    public ClientboundPluginMessagePlayPacket() {}

    public ClientboundPluginMessagePlayPacket(Key key, byte[] data) {
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

    public static class Listener implements IPacketListener<ClientboundPluginMessagePlayPacket> {
        @Override
        public void handle(ClientboundPluginMessagePlayPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            EventUtils.createPluginMessageEventAndAsyncFire(ProtocolDirection.CLIENTBOUND, packet.key, packet.data, proxy)
                    .whenComplete((event, throwable) -> {
                        PluginMessageResult pluginMessageResult = PluginMessageResult.NONE;

                        if (throwable == null) {
                            pluginMessageResult = event.getResultWithPluginMessageResult();
                            packet.data = event.getData();
                            packet.key = event.getKey();
                        }

                        if (pluginMessageResult == PluginMessageResult.FORWARD ||
                                pluginMessageResult == PluginMessageResult.NONE ||
                                pluginMessageResult == PluginMessageResult.UNKNOWN) {
                            if (ctx.channel().eventLoop().inEventLoop()) {
                                send(ctx, packet);
                            } else {
                                ctx.channel().eventLoop().execute(() -> send(ctx, packet));
                            }
                        }
                    });
        }

        private void send(ChannelHandlerContext ctx, ClientboundPluginMessagePlayPacket packet) {
            ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().toUpstream(packet);
        }
    }
}
