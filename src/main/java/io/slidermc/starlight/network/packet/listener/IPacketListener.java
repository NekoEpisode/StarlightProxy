package io.slidermc.starlight.network.packet.listener;

import io.slidermc.starlight.network.packet.IMinecraftPacket;

public interface IPacketListener<T extends IMinecraftPacket> {
    void handle(T packet);
}
