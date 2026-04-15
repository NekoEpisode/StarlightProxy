package io.slidermc.starlight.network.packet.packets.clientbound.status;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ClientboundPongResponsePacket implements IMinecraftPacket {
    private long payload;

    public ClientboundPongResponsePacket() {}

    public ClientboundPongResponsePacket(long payload) {
        this.payload = payload;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        byteBuf.writeLong(payload);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.payload = byteBuf.readLong();
    }

    public long getPayload() {
        return payload;
    }

    public void setPayload(long payload) {
        this.payload = payload;
    }

    public static class Listener implements IPacketListener<ClientboundPongResponsePacket> {
        @Override
        public void handle(ClientboundPongResponsePacket packet, ChannelHandlerContext ctx) {
            // TODO: 待实现
        }
    }
}
