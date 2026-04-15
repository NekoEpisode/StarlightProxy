package io.slidermc.starlight.network.packet.packets.serverbound.status;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundPongResponsePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundPingRequestPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundPingRequestPacket.class);
    private long payload;

    public ServerboundPingRequestPacket() {}

    public ServerboundPingRequestPacket(long payload) {
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

    public static class Listener implements IPacketListener<ServerboundPingRequestPacket> {
        @Override
        public void handle(ServerboundPingRequestPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            log.debug("收到PingRequest");
            ctx.channel().writeAndFlush(new ClientboundPongResponsePacket(packet.getPayload()));
        }
    }
}
