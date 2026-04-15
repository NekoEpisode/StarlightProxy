package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginAckPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientboundLoginSuccessPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundLoginSuccessPacket.class);
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
            log.debug("收到下游LoginSuccess");
            StarlightMinecraftClient client = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient();
            client.setInboundState(ProtocolState.CONFIGURATION);
            ctx.channel().writeAndFlush(new ServerboundLoginAckPacket()).addListener(_ -> {
                client.setOutboundState(ProtocolState.CONFIGURATION);
                client.callLoginComplete();
            });
        }
    }
}
