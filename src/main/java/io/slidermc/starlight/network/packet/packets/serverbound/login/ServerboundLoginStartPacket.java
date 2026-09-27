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
                Component component = Component.text("Unsupported protocol version: " + context.getHandshakeInformation().getOriginalProtocolVersion());
                if ((context.getHandshakeInformation().getOriginalProtocolVersion() & 0x40000000) != 0) {
                    component = component.append(Component.text("\n(Are you using snapshot versions?)"));
                }
                component = component.color(NamedTextColor.RED);
                ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(component))
                        .addListener(_ -> ctx.channel().close());
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
                doLogin(ctx, proxy, packet, context,
                        preLoginEvent.isForceOnlineMode(), preLoginEvent.isForceOfflineMode());
            };

            if (!preLoginEvent.hasIntents()) {
                loginAction.run();
            } else {
                preLoginEvent.tryComplete();
                preLoginEvent.getCompletionFuture().thenRun(() ->
                        ctx.channel().eventLoop().execute(loginAction));
            }
        }

        /**
         * 执行登录决策。
         *
         * <p>这里有两个相互独立的维度
         * <ul>
         *   <li><b>是否加密</b> —— 由全局 {@code encryption} 配置决定。Starlight 允许离线连接
         *       也走加密，所以进入本方法的上半分支并不等于"正版验证"。</li>
         *   <li><b>是否向 Mojang 验证</b> —— 由 {@code forceOnline} / {@code forceOffline} /
         *       全局 {@code online-mode} 共同决定，实际判定发生在
         *       {@code ServerboundEncryptionResponsePacket}，那里此时已经装好加密管道。</li>
         * </ul>
         *
         * <p>因此 {@code forceOffline} 的语义是"跳过 Mojang 验证"，而<b>不是</b>"跳过加密"。
         * 它只改变档案来源（离线 UUID），加密与否取决于 {@code encryption}。
         * 基岩版客户端没有 Mojang 会话，必须走这条路。
         *
         * @param forceOnline  强制走正版验证
         * @param forceOffline 强制跳过 Mojang 验证
         */
        private static void doLogin(ChannelHandlerContext ctx, StarlightProxy proxy,
                                     ServerboundLoginStartPacket packet, ConnectionContext context,
                                     boolean forceOnline, boolean forceOffline) {
            if (forceOnline || proxy.getConfig().isEncryption()) {
                if (forceOnline) {
                    context.setPerConnectionOnlineMode(true);
                }
                if (forceOffline) {
                    context.setPerConnectionOfflineMode(true);
                }
                log.debug("玩家 {} 进入{}流程", packet.getUsername(),
                        (forceOnline || (proxy.getConfig().isOnlineMode() && !forceOffline))
                                ? "正版验证" : "加密登录");
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
                LoginHelper.completeLogin(ctx, proxy, gpEvent.getGameProfile(), gpEvent.isOnlineMode());
            }
        }

        private static void disconnect(ChannelHandlerContext ctx) {
            ctx.channel().writeAndFlush(
                    new ClientboundDisconnectLoginPacket(LOGIN_DENIED_COMPONENT)
            ).addListener(_ -> ctx.channel().close());
        }
    }
}
