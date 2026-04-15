package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftPacketEncoder extends MessageToByteEncoder<IMinecraftPacket> {
    private static final Logger log = LoggerFactory.getLogger(MinecraftPacketEncoder.class);
    private final PacketRegistry packetRegistry;

    public MinecraftPacketEncoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, IMinecraftPacket packet, ByteBuf out) throws Exception {
        ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
        if (context == null) {
            log.error("ConnectionContext is null while encoding, dropping packet: {}", packet.getClass().getName());
            return;
        }

        ProtocolVersion protocolVersion = context.getHandshakeInformation().getProtocolVersion();
        if (protocolVersion == null) {
            log.error("ProtocolVersion is null while encoding (still in HANDSHAKE?), dropping packet: {}", packet.getClass().getName());
            return;
        }

        int packetId = packetRegistry.getPacketId(
                protocolVersion.getProtocolVersionCode(),
                context.getOutboundState(),
                ProtocolDirection.CLIENTBOUND,
                packet
        );

        // 先把 [packetId VarInt + payload] 写入临时 buffer，才能知道准确的 length
        ByteBuf payload = ctx.alloc().buffer();
        try {
            MinecraftCodecUtils.writeVarInt(payload, packetId);
            packet.encode(payload, protocolVersion);
            // 最终格式：[length VarInt][packetId VarInt][payload bytes]
            MinecraftCodecUtils.writeVarInt(out, payload.readableBytes());
            out.writeBytes(payload);
        } finally {
            payload.release();
        }
    }
}
