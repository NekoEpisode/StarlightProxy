package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.client.LoginResult;
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

import java.util.UUID;

public class ClientboundLoginSuccessPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundLoginSuccessPacket.class);
    private GameProfile gameProfile;
    private UUID sessionId;

    public ClientboundLoginSuccessPacket() {}

    public ClientboundLoginSuccessPacket(GameProfile gameProfile, UUID sessionId) {
        this.gameProfile = gameProfile;
        this.sessionId = sessionId;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeGameProfile(byteBuf, gameProfile);

        if (protocolVersion.isGreaterThanOrEqual(ProtocolVersion.MINECRAFT_26_2)) {
            MinecraftCodecUtils.writeUUID(byteBuf, sessionId != null ? sessionId : new UUID(0L, 0L));
        }
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.gameProfile = MinecraftCodecUtils.readGameProfile(byteBuf);

        if (protocolVersion.isGreaterThanOrEqual(ProtocolVersion.MINECRAFT_26_2)) {
            this.sessionId = MinecraftCodecUtils.readUUID(byteBuf);
        }
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public void setGameProfile(GameProfile gameProfile) {
        this.gameProfile = gameProfile;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public static class Listener implements IPacketListener<ClientboundLoginSuccessPacket> {
        @Override
        public void handle(ClientboundLoginSuccessPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            log.debug("收到下游LoginSuccess");
            StarlightMinecraftClient client = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient();
            client.setInboundState(ProtocolState.CONFIGURATION);
            ctx.channel().writeAndFlush(new ServerboundLoginAckPacket()).addListener(_ -> {
                client.setOutboundState(ProtocolState.CONFIGURATION);
                client.completeLogin(new LoginResult.Success());
            });
        }
    }
}
