package io.slidermc.starlight.network.packet;

import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public interface IMinecraftPacket {
    void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion);
    void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion);
}
