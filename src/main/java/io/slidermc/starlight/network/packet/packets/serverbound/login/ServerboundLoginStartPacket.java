package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundEncryptionRequestPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.helper.LoginHelper;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.UUIDUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ServerboundLoginStartPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundLoginStartPacket.class);

    private String username;
    private UUID uuid;

    public ServerboundLoginStartPacket() {}

    public ServerboundLoginStartPacket(String username, UUID uuid) {
        this.username = username;
        this.uuid = uuid;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, this.username);
        MinecraftCodecUtils.writeUUID(byteBuf, this.uuid);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.username = MinecraftCodecUtils.readString(byteBuf);
        this.uuid = MinecraftCodecUtils.readUUID(byteBuf);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public static class Listener implements IPacketListener<ServerboundLoginStartPacket> {
        @Override
        public void handle(ServerboundLoginStartPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            if (context.getHandshakeInformation().getProtocolVersion() == ProtocolVersion.UNKNOWN) {
                log.debug("不支持的版本，踢出");
                ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(
                        Component.text("Unsupported protocol version: " + context.getHandshakeInformation().getOriginalProtocolVersion())
                                .color(NamedTextColor.RED)
                )).addListener(_ -> ctx.channel().close());
                return;
            }

            if (proxy.getConfig().isEncryption()) {
                log.debug("玩家 {} 进入{}流程", packet.getUsername(),
                        proxy.getConfig().isOnlineMode() ? "正版验证" : "加密登录");
                // 暂存用户名供 EncryptionResponse.Listener 使用
                context.setPendingUsername(packet.getUsername());
                // 生成 verifyToken 并存入 context 待验证
                byte[] verifyToken = proxy.getEncryptionManager().generateVerifyToken();
                context.setVerifyToken(verifyToken);
                ctx.channel().writeAndFlush(new ClientboundEncryptionRequestPacket(
                        "",
                        proxy.getEncryptionManager().getPublicKeyBytes(),
                        verifyToken,
                        true
                )).addListener(_ -> log.debug("已发送 EncryptionRequest"));
            } else {
                log.debug("玩家 {} 以离线模式登录", packet.getUsername());
                GameProfile profile = new GameProfile(
                        packet.username,
                        UUIDUtils.generateOfflineUuid(packet.username),
                        List.of()
                );
                LoginHelper.completeLogin(ctx, proxy, profile);
            }
        }
    }
}
