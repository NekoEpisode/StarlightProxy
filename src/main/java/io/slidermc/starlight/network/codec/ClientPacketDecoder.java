package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
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

import java.util.List;

/**
 * Decoder for the downstream (backend server → proxy) connection.
 * Reads protocol state and version directly from {@link StarlightMinecraftClient}
 * — no {@code CONNECTION_CONTEXT} attribute needed.
 * The backend server always sends CLIENTBOUND packets.
 * The proxy-client never receives a HANDSHAKE packet from the server, so there is
 * no HANDSHAKE special-case here.
 */
public class ClientPacketDecoder extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger(ClientPacketDecoder.class);
    private final PacketRegistry packetRegistry;
    private final StarlightMinecraftClient client;

    public ClientPacketDecoder(PacketRegistry packetRegistry, StarlightMinecraftClient client) {
        this.packetRegistry = packetRegistry;
        this.client = client;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() < 1) {
            return;
        }

        byteBuf.markReaderIndex();

        // 逐字节安全读取 length VarInt，若中途数据不足则 reset 等待更多数据
        int length = tryReadVarInt(byteBuf);
        if (length == Integer.MIN_VALUE) {
            byteBuf.resetReaderIndex();
            return;
        }
        if (byteBuf.readableBytes() < length) {
            byteBuf.resetReaderIndex();
            return;
        }

        // contentStart = reader index of [packetId VarInt + payload], used to capture raw bytes if needed
        int contentStart = byteBuf.readerIndex();
        int packetId = MinecraftCodecUtils.readVarInt(byteBuf);

        ProtocolVersion protocolVersion = client.getProtocolVersion();
        ProtocolState inboundState = client.getInboundState();

        if (protocolVersion == null || inboundState == null) {
            log.error("protocolVersion or inboundState is null while decoding from downstream server, dropping packet 0x{}", Integer.toHexString(packetId));
            byteBuf.skipBytes(byteBuf.readableBytes());
            return;
        }

        if (!packetRegistry.hasPacket(protocolVersion.getProtocolVersionCode(), inboundState, ProtocolDirection.CLIENTBOUND, packetId)) {
            if (inboundState == ProtocolState.CONFIGURATION || inboundState == ProtocolState.PLAY) {
                // 透明转发：把 [packetId VarInt + payload] 整段原样包装
                byteBuf.readerIndex(contentStart);
                byte[] raw = new byte[length];
                byteBuf.readBytes(raw);
                list.add(new RawPacket(raw));
            } else {
                // LOGIN/STATUS 阶段不允许未知包，直接丢弃
                log.warn("Unknown CLIENTBOUND packet 0x{} in state {}, dropping", Integer.toHexString(packetId), inboundState);
                byteBuf.readerIndex(contentStart + length);
            }
            return;
        }

        IMinecraftPacket packet = packetRegistry.createPacket(
                protocolVersion.getProtocolVersionCode(),
                inboundState,
                ProtocolDirection.CLIENTBOUND,
                packetId
        );
        int payloadLength = length - (byteBuf.readerIndex() - contentStart);
        ByteBuf slice = byteBuf.readSlice(payloadLength);
        packet.decode(slice, protocolVersion);
        list.add(packet);
    }

    /**
     * 安全读取 VarInt：若任意一个字节不可读则返回 {@code Integer.MIN_VALUE}（调用方应 reset 并等待更多数据）。
     * 正常情况返回解码后的值；若 VarInt 超过 5 字节则抛出 {@link DecoderException}。
     */
    private static int tryReadVarInt(ByteBuf buf) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            if (!buf.isReadable()) {
                return Integer.MIN_VALUE; // 数据不足，需要等待
            }
            read = buf.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new DecoderException("VarInt is too big");
            }
        } while ((read & 0b10000000) != 0);
        return result;
    }
}
