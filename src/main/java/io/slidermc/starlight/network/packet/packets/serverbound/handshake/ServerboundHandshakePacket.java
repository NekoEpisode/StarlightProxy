package io.slidermc.starlight.network.packet.packets.serverbound.handshake;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.NextState;
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

    public ServerboundHandshakePacket() {}

    public ServerboundHandshakePacket(int protocolVersion, String serverAddress, short serverPort, int nextState) {
        this.protocolVersion = protocolVersion;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.nextState = nextState;
    }

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

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public short getServerPort() {
        return serverPort;
    }

    public void setServerPort(short serverPort) {
        this.serverPort = serverPort;
    }

    public int getNextState() {
        return nextState;
    }

    public void setNextState(int nextState) {
        this.nextState = nextState;
    }

    public static class Listener implements IPacketListener<ServerboundHandshakePacket> {
        @Override
        public void handle(ServerboundHandshakePacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.getHandshakeInformation().setOriginalProtocolVersion(packet.protocolVersion);
            context.getHandshakeInformation().setProtocolVersion(ProtocolVersion.getByProtocolVersionCode(packet.protocolVersion));
            log.debug("已设置协议版本号: {}", context.getHandshakeInformation().getProtocolVersion().name());
            context.getHandshakeInformation().setNextState(NextState.getById(packet.nextState));
            context.getHandshakeInformation().setServerAddress(packet.getServerAddress());
            context.getHandshakeInformation().setServerPort(packet.getServerPort());
            switch (packet.nextState) {
                case 1 -> {
                    // Status
                    log.debug("Next State: STATUS");
                    context.setInboundState(ProtocolState.STATUS);
                    context.setOutboundState(ProtocolState.STATUS);
                }
                case 2 -> {
                    // Login
                    log.debug("Next State: LOGIN");
                    context.setInboundState(ProtocolState.LOGIN);
                    context.setOutboundState(ProtocolState.LOGIN);
                }
                case 3 -> {
                    // Transfer
                    log.debug("Next State: Transfer");
                    context.setInboundState(ProtocolState.LOGIN);
                    context.setOutboundState(ProtocolState.LOGIN);
                }
                default -> {
                    log.warn("Unknown Next State: {}, Closing connection", packet.nextState);
                    ctx.channel().close();
                }
            }
        }
    }
}
