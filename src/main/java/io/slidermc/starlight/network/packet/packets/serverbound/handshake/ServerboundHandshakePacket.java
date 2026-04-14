package io.slidermc.starlight.network.packet.packets.serverbound.handshake;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundHandshakePacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundHandshakePacket.class);
    private int protocolVersion;
    private String serverAddress;
    private short serverPort;
    private int nextState;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, this.protocolVersion);
        MinecraftCodecUtils.writeString(byteBuf, this.serverAddress);
        byteBuf.writeShort(this.serverPort);
        MinecraftCodecUtils.writeVarInt(byteBuf, this.nextState);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.protocolVersion = MinecraftCodecUtils.readVarInt(byteBuf);
        this.serverAddress = MinecraftCodecUtils.readString(byteBuf);
        this.serverPort = byteBuf.readShort();
        this.nextState = MinecraftCodecUtils.readVarInt(byteBuf);
    }

    public static class Listener implements IPacketListener<ServerboundHandshakePacket> {
        @Override
        public void handle(ServerboundHandshakePacket packet, ChannelHandlerContext ctx) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.setProtocolVersion(ProtocolVersion.getByProtocolVersionCode(packet.protocolVersion));
            log.debug("已设置协议版本号: {}", context.getProtocolVersion().name());
            switch (packet.nextState) {
                case 1 -> {
                    // Status
                    log.debug("Next State: STATUS");
                    context.setInboundState(ProtocolState.STATUS);
                    context.setOutboundState(ProtocolState.STATUS);
                }
                case 2 -> {
                    // Login
                }
                case 3 -> {
                    // Transfer
                }
            }
        }
    }
}
