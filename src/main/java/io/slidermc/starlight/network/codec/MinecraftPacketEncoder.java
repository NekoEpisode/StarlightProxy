package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.slidermc.starlight.network.packet.IMinecraftPacket;

public class MinecraftPacketEncoder extends MessageToByteEncoder<IMinecraftPacket> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, IMinecraftPacket iMinecraftPacket, ByteBuf byteBuf) throws Exception {

    }
}
