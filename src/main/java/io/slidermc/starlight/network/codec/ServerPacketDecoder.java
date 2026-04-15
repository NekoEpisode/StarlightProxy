package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RawPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ServerPacketDecoder extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger(ServerPacketDecoder.class);
    private final PacketRegistry packetRegistry;

    public ServerPacketDecoder(PacketRegistry packetRegistry) {
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

        // contentStart = reader index of [packetId VarInt + payload], used to capture raw bytes if needed
        int contentStart = byteBuf.readerIndex();
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
            IMinecraftPacket packet = packetRegistry.createPacket(ProtocolVersion.ALL_VERSION.getProtocolVersionCode(), ProtocolState.HANDSHAKE, ProtocolDirection.SERVERBOUND, packetId);
            packet.decode(byteBuf, ProtocolVersion.ALL_VERSION);
            list.add(packet);
        } else {
            // 如果已经经过Handshake，那ConnectionContext里的ProtocolVersion应该不为null了
            ProtocolVersion protocolVersion = context.getHandshakeInformation().getProtocolVersion();
            if (protocolVersion == null) {
                ctx.channel().close(); // 预料之外的行为
                log.error("ConnectionContext's protocolVersion field is null?? why? Anyway, drop the connection");
                return;
            }

            ProtocolState inboundState = context.getInboundState();
            if (!packetRegistry.hasPacket(protocolVersion.getProtocolVersionCode(), inboundState, ProtocolDirection.SERVERBOUND, packetId)) {
                if (inboundState == ProtocolState.CONFIGURATION || inboundState == ProtocolState.PLAY) {
                    // 透明转发：把 [packetId VarInt + payload] 整段原样包装
                    byteBuf.readerIndex(contentStart);
                    byte[] raw = new byte[length];
                    byteBuf.readBytes(raw);
                    list.add(new RawPacket(raw));
                } else {
                    // HANDSHAKE/LOGIN/STATUS 阶段不允许未知包，直接丢弃
                    log.warn("Unknown SERVERBOUND packet 0x{} in state {}, dropping", Integer.toHexString(packetId), inboundState);
                    byteBuf.readerIndex(contentStart + length);
                }
                return;
            }

            IMinecraftPacket packet = packetRegistry.createPacket(
                    protocolVersion.getProtocolVersionCode(),
                    inboundState,
                    ProtocolDirection.SERVERBOUND,
                    packetId
            );
            packet.decode(byteBuf, protocolVersion);
            list.add(packet);
        }
    }
}
