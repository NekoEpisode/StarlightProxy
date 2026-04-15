package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RawPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encoder for the downstream (proxy → backend server) connection.
 * Reads protocol state and version directly from {@link StarlightMinecraftClient}
 * — no {@code CONNECTION_CONTEXT} attribute needed.
 * The client always sends SERVERBOUND packets.
 */
public class ClientPacketEncoder extends MessageToByteEncoder<IMinecraftPacket> {
    private static final Logger log = LoggerFactory.getLogger(ClientPacketEncoder.class);
    private final PacketRegistry packetRegistry;
    private final StarlightMinecraftClient client;

    public ClientPacketEncoder(PacketRegistry packetRegistry, StarlightMinecraftClient client) {
        this.packetRegistry = packetRegistry;
        this.client = client;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, IMinecraftPacket packet, ByteBuf out) throws Exception {
        // RawPacket: write content bytes directly, no registry lookup needed
        if (packet instanceof RawPacket rawPacket) {
            byte[] content = rawPacket.getContent();
            MinecraftCodecUtils.writeVarInt(out, content.length);
            out.writeBytes(content);
            return;
        }

        ProtocolState outboundState = client.getOutboundState();
        ProtocolVersion protocolVersion;

        // HANDSHAKE: outboundState is null before login() is called, or explicitly HANDSHAKE.
        // The ServerboundHandshakePacket is always encoded with ALL_VERSION under HANDSHAKE state.
        if (outboundState == null || outboundState == ProtocolState.HANDSHAKE) {
            outboundState = ProtocolState.HANDSHAKE;
            protocolVersion = ProtocolVersion.ALL_VERSION;
        } else {
            protocolVersion = client.getProtocolVersion();
            if (protocolVersion == null) {
                log.error("ProtocolVersion is null while encoding, dropping packet: {}", packet.getClass().getName());
                return;
            }
        }

        int packetId = packetRegistry.getPacketId(
                protocolVersion.getProtocolVersionCode(),
                outboundState,
                ProtocolDirection.SERVERBOUND,
                packet
        );

        ByteBuf payload = ctx.alloc().buffer();
        try {
            MinecraftCodecUtils.writeVarInt(payload, packetId);
            packet.encode(payload, protocolVersion);
            MinecraftCodecUtils.writeVarInt(out, payload.readableBytes());
            out.writeBytes(payload);
        } finally {
            payload.release();
        }
    }
}

