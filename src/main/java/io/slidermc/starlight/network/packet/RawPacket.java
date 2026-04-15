package io.slidermc.starlight.network.packet;

import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

/**
 * A passthrough packet that carries already-framed content bytes verbatim.
 * <p>
 * {@code content} = everything AFTER the length-prefix VarInt, i.e.:
 * {@code [packetId VarInt bytes][payload bytes]}.
 * <p>
 * Encoders and decoders handle {@code RawPacket} specially — they never call
 * {@link #encode} or {@link #decode} on it; instead the encoder writes
 * {@code [length VarInt][content]} directly, and the decoder captures the
 * content bytes without parsing them.
 * <p>
 * Handlers forward it to the opposite channel without dispatching to any listener.
 */
public class RawPacket implements IMinecraftPacket {
    private final byte[] content;

    public RawPacket(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        throw new UnsupportedOperationException("RawPacket must be handled directly by the encoder");
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        throw new UnsupportedOperationException("RawPacket must be handled directly by the decoder");
    }
}

