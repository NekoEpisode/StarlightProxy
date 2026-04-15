package io.slidermc.starlight.network.packet.listener;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.packet.IMinecraftPacket;

public interface IPacketListener<T extends IMinecraftPacket> {
    void handle(T packet, ChannelHandlerContext ctx, StarlightProxy proxy);
}
