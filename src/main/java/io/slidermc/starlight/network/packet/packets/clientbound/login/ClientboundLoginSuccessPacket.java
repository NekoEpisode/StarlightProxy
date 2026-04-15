package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ClientboundLoginSuccessPacket implements IMinecraftPacket {
    private GameProfile gameProfile;

    public ClientboundLoginSuccessPacket() {}

    public ClientboundLoginSuccessPacket(GameProfile gameProfile) {
        this.gameProfile = gameProfile;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeGameProfile(byteBuf, gameProfile);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.gameProfile = MinecraftCodecUtils.readGameProfile(byteBuf);
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public void setGameProfile(GameProfile gameProfile) {
        this.gameProfile = gameProfile;
    }

    public static class Listener implements IPacketListener<ClientboundLoginSuccessPacket> {
        @Override
        public void handle(ClientboundLoginSuccessPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // TODO: 待实现
        }
    }
}
