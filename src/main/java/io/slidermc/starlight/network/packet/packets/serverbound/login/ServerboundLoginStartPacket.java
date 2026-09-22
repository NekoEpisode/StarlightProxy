package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.GameProfileRequestEvent;
import io.slidermc.starlight.api.event.events.internal.PreLoginEvent;
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
        private static final Component LOGIN_DENIED_COMPONENT = Component.text("Login denied").color(NamedTextColor.RED);

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

            PreLoginEvent preLoginEvent = new PreLoginEvent(context, packet.getUsername());
            proxy.getEventManager().fire(preLoginEvent);

            if (preLoginEvent.isDenied()) {
                Component reason = preLoginEvent.getDenyReason();
                if (reason == null) {
                    reason = LOGIN_DENIED_COMPONENT;
                }
                ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(reason))
                        .addListener(_ -> ctx.channel().close());
                return;
            }

            Runnable loginAction = () -> {
                if (!ctx.channel().isActive()) return;
                doLogin(ctx, proxy, packet, context, preLoginEvent.isForceOnlineMode());
            };

            if (!preLoginEvent.hasIntents()) {
                loginAction.run();
            } else {
                preLoginEvent.tryComplete();
                preLoginEvent.getCompletionFuture().thenRun(() ->
                        ctx.channel().eventLoop().execute(loginAction));
            }
        }

        private static void doLogin(ChannelHandlerContext ctx, StarlightProxy proxy,
                                     ServerboundLoginStartPacket packet, ConnectionContext context,
                                     boolean forceOnline) {
            if (forceOnline || proxy.getConfig().isEncryption()) {
                if (forceOnline) {
                    context.setPerConnectionOnlineMode(true);
                }
                log.debug("玩家 {} 进入{}流程", packet.getUsername(),
                        (forceOnline || proxy.getConfig().isOnlineMode()) ? "正版验证" : "加密登录");
                context.setPendingUsername(packet.getUsername());
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
                GameProfileRequestEvent gpEvent = new GameProfileRequestEvent(context, profile, false);
                proxy.getEventManager().fire(gpEvent);
                if (gpEvent.isCancelled()) {
                    disconnect(ctx);
                    return;
                }
                LoginHelper.completeLogin(ctx, proxy, gpEvent.getGameProfile());
            }
        }

        private static void disconnect(ChannelHandlerContext ctx) {
            ctx.channel().writeAndFlush(
                    new ClientboundDisconnectLoginPacket(LOGIN_DENIED_COMPONENT)
            ).addListener(_ -> ctx.channel().close());
        }
    }
}
