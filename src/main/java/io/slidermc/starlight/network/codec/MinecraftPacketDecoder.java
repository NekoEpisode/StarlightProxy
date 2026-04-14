package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.PacketRegistry;

import java.util.List;

public class MinecraftPacketDecoder extends ByteToMessageDecoder {
    private final PacketRegistry packetRegistry;

    public MinecraftPacketDecoder(PacketRegistry packetRegistry) {
        this.packetRegistry = packetRegistry;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        if (byteBuf.readableBytes() < 1) {
            return;
        }

        int length = MinecraftCodecUtils.readVarInt(byteBuf);
        int packetId = MinecraftCodecUtils.readVarInt(byteBuf);
        if (length > byteBuf.readableBytes()) {
            return;
        }
        // IMinecraftPacket packet = packetRegistry.createPacket();
        // TODO: WIP
    }
}
