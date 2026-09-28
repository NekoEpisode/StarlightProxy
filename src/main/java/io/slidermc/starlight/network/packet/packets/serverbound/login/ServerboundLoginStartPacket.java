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
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
                disconnectUnsupportedVersion(ctx, proxy, context);
                return;
            }

            PreLoginEvent preLoginEvent = new PreLoginEvent(context, packet.getUsername());

            proxy.getEventManager().fire(preLoginEvent)
                    // 登录插件查询是"一问一答"的：处理器在 PreLoginEvent 里发起查询，
                    // 必须等全部应答结束再推进登录，否则应答会与加密请求等后续包交错
                    .thenCompose(_ -> context.getLoginQueries().allSettled())
                    .thenRun(() ->
                            ctx.channel().eventLoop().execute(() -> {
                                if (!ctx.channel().isActive()) return;
                                if (preLoginEvent.isDenied()) {
                                    denyLogin(ctx, proxy, preLoginEvent.getDenyReason());
                                    return;
                                }
                                doLogin(ctx, proxy, packet, context,
                                        preLoginEvent.isForceOnlineMode(), preLoginEvent.isForceOfflineMode());
                            }));
        }

        /**
         * 拒绝客户端请求的协议版本。
         *
         * <p>只陈述实际收到的版本号，不提示"请升级客户端"：无法区分是客户端过旧还是代理尚未适配
         * 更新的正式版，武断的建议反而会误导玩家。
         *
         * <p>快照客户端的协议号带 {@code 0x40000000} 位，该版本号必然是代理没见过的，因此额外附一句
         * 说明——这是唯一能从版本号本身确定的原因。
         *
         * <p>登录阶段拿不到客户端 {@code ClientInformation}，因此按代理默认语言翻译。
         *
         * @param ctx     上游连接的上下文
         * @param proxy   代理实例
         * @param context 连接上下文
         */
        private static void disconnectUnsupportedVersion(ChannelHandlerContext ctx, StarlightProxy proxy,
                                                         ConnectionContext context) {
            int originalVersion = context.getHandshakeInformation().getOriginalProtocolVersion();
            String locale = proxy.getTranslateManager().getActiveLocale();

            Component component = MiniMessageUtils.MINI_MESSAGE.deserialize(
                    proxy.getTranslateManager().translate(locale, "starlight.disconnect.unsupported_version"),
                    Placeholder.parsed("version", String.valueOf(originalVersion)));

            if ((originalVersion & 0x40000000) != 0) {
                component = component.append(Component.newline()).append(
                        MiniMessageUtils.MINI_MESSAGE.deserialize(
                                proxy.getTranslateManager().translate(
                                        locale, "starlight.disconnect.unsupported_version.snapshot_hint")));
            }

            ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(component))
                    .addListener(_ -> ctx.channel().close());
        }

        private static void denyLogin(ChannelHandlerContext ctx, StarlightProxy proxy, Component reason) {
            ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(
                            reason != null ? reason : MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    proxy.getTranslateManager().translate(
                                            proxy.getTranslateManager().getActiveLocale(),
                                            "starlight.disconnect.login_denied"))))
                    .addListener(_ -> ctx.channel().close());
        }

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

                boolean willAuthenticate = forceOnline
                        || (!forceOffline && proxy.getConfig().isOnlineMode());

                log.debug("玩家 {} 进入{}流程", packet.getUsername(),
                        willAuthenticate ? "正版验证" : "加密登录");
                context.setPendingUsername(packet.getUsername());
                byte[] verifyToken = proxy.getEncryptionManager().generateVerifyToken();
                context.setVerifyToken(verifyToken);
                ctx.channel().writeAndFlush(new ClientboundEncryptionRequestPacket(
                        "",
                        proxy.getEncryptionManager().getPublicKeyBytes(),
                        verifyToken,
                        willAuthenticate
                )).addListener(_ -> log.debug("已发送 EncryptionRequest (shouldAuthenticate={})",
                        willAuthenticate));
            } else {
                log.debug("玩家 {} 以离线模式登录", packet.getUsername());
                GameProfile profile = new GameProfile(
                        packet.username,
                        UUIDUtils.generateOfflineUuid(packet.username),
                        List.of()
                );
                GameProfileRequestEvent gpEvent = new GameProfileRequestEvent(context, profile, false);
                // 处理器可能暂停派发（例如异步查询皮肤），因此必须等事件定稿再读取档案，
                // 否则插件的档案改写与取消会被静默忽略
                proxy.getEventManager().fire(gpEvent).thenRun(() ->
                        ctx.channel().eventLoop().execute(() -> completeOfflineLogin(ctx, proxy, gpEvent)));
            }
        }

        /**
         * 在档案事件定稿后完成离线登录。
         *
         * @param ctx     通道上下文
         * @param proxy   代理实例
         * @param gpEvent 已定稿的档案请求事件
         */
        private static void completeOfflineLogin(ChannelHandlerContext ctx,
                                                 StarlightProxy proxy,
                                                 GameProfileRequestEvent gpEvent) {
            if (!ctx.channel().isActive()) {
                return;
            }
            if (gpEvent.isCancelled()) {
                disconnect(ctx, proxy);
                return;
            }
            LoginHelper.completeLogin(ctx, proxy, gpEvent.getGameProfile(), gpEvent.isOnlineMode());
        }

        /**
         * 以"登录被拒绝"为由断开连接。
         *
         * <p>登录阶段拿不到客户端 {@code ClientInformation}，因此按代理默认语言翻译。
         *
         * @param ctx   上游连接的上下文
         * @param proxy 代理实例
         */
        private static void disconnect(ChannelHandlerContext ctx, StarlightProxy proxy) {
            ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    proxy.getTranslateManager().translate(
                                            proxy.getTranslateManager().getActiveLocale(),
                                            "starlight.disconnect.login_denied"))))
                    .addListener(_ -> ctx.channel().close());
        }
    }
}
