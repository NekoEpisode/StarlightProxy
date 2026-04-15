package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MinecraftPacketDecoder extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger(MinecraftPacketDecoder.class);
    private final PacketRegistry packetRegistry;

    public MinecraftPacketDecoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() < 1) {
            return;
        }

        byteBuf.markReaderIndex();

        int length = MinecraftCodecUtils.readVarInt(byteBuf);
        if (byteBuf.readableBytes() < length) {
            byteBuf.resetReaderIndex();
            return;
        }

        int packetId = MinecraftCodecUtils.readVarInt(byteBuf);

        ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
        if (context == null) {
            context = new ConnectionContext();
            ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).set(context);
        }
        if (context.getInboundState() == ProtocolState.HANDSHAKE) {
            if (packetId != 0x00) {
                ctx.channel().close();
                return;
            }
            IMinecraftPacket packet = packetRegistry.createPacket(ProtocolVersion.ALL_VERSION.getProtocolVersionCode(), ProtocolState.HANDSHAKE, ProtocolDirection.SERVERBOUND, packetId); // packetId应该为0
            packet.decode(byteBuf, ProtocolVersion.ALL_VERSION);
            list.add(packet);
        } else {
            // 如果已经经过Handshake，那ConnectionContext里的ProtocolVersion应该不为null了
            ProtocolVersion protocolVersion = context.getHandshakeInformation().getProtocolVersion();
            if (context.getHandshakeInformation().getProtocolVersion() == null) {
                ctx.channel().close(); // 预料之外的行为
                log.error("ConnectionContext's protocolVersion field is null?? why? Anyway, drop the connection");
                return;
            }
            IMinecraftPacket packet = packetRegistry.createPacket(
                    protocolVersion.getProtocolVersionCode(),
                    context.getInboundState(),
                    ProtocolDirection.SERVERBOUND, packetId
            );
            packet.decode(byteBuf, protocolVersion);
            list.add(packet);
        }
    }
}
