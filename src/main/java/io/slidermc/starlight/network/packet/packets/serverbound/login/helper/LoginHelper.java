package io.slidermc.starlight.network.packet.packets.serverbound.login.helper;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.PlayerLoginEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundLoginSuccessPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundSetCompressionPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 登录流程公共逻辑：
 * 创建 ProxiedPlayer → (可选) SetCompression → LoginSuccess
 * <p>
 * 无论离线模式还是正版验证，最终都调用 {@link #completeLogin}。
 */
public final class LoginHelper {
    private static final Logger log = LoggerFactory.getLogger(LoginHelper.class);

    private LoginHelper() {}

    /**
     * 完成登录流程。
     *
     * @param ctx        上游客户端的 ChannelHandlerContext
     * @param proxy      代理实例
     * @param profile    已确定的 GameProfile（离线或正版均可）
     * @param onlineMode 本次登录是否通过了 Mojang 正版验证，
     *                   应取自 {@link io.slidermc.starlight.api.event.events.internal.GameProfileRequestEvent#isOnlineMode()}
     */
    public static void completeLogin(ChannelHandlerContext ctx, StarlightProxy proxy, GameProfile profile,
                                     boolean onlineMode) {
        ProxiedPlayer player = new ProxiedPlayer(profile, ctx.channel(), proxy, true, onlineMode);

        // 到这里 profile 的 UUID 才是权威的：离线路径由代理分配，正版路径经 Mojang 验证。
        // 因此重复登录只能在此判定——放在 LoginStart 阶段会漏掉正版玩家的 UUID。
        // 检查与注册必须是一次原子操作，否则并发的同名连接会同时通过。
        if (!proxy.getPlayerManager().tryAddPlayer(player)) {
            denyDuplicateLogin(ctx, proxy, profile);
            return;
        }

        log.debug("已创建ProxiedPlayer对象: {}", player);
        player.getConnectionContext().setPlayer(player);
        PlayerLoginEvent playerLoginEvent = new PlayerLoginEvent(player);
        proxy.getEventManager().fire(playerLoginEvent).thenRun(() -> {
            Runnable loginAction = () -> {
                log.info(
                        proxy.getTranslateManager().translate("starlight.logging.info.player.join"),
                        player.getGameProfile().username(),
                        player.getGameProfile().uuid(),
                        player.getChannel().remoteAddress()
                );

                int threshold = proxy.getConfig().getCompressThreshold();
                if (threshold >= 0) {
                    ctx.channel().writeAndFlush(new ClientboundSetCompressionPacket(threshold)).addListener(_ -> {
                        CompressionHandlers.install(ctx, proxy, threshold);
                        log.debug("上游已启用压缩，阈值: {}", threshold);
                        sendLoginSuccess(ctx, player);
                    });
                } else {
                    sendLoginSuccess(ctx, player);
                }
            };
            if (ctx.channel().eventLoop().inEventLoop()) {
                loginAction.run();
            } else {
                ctx.channel().eventLoop().execute(loginAction);
            }
        });
    }

    /**
     * 拒绝一次重复登录。
     *
     * <p>此刻客户端仍处于登录阶段、尚未收到 {@code LoginSuccess}、压缩也未启用，
     * 因此直接发登录阶段的断开包是合法且能被客户端正常显示原因的。
     *
     * <p>日志走翻译，玩家提示取连接语言：登录阶段还拿不到客户端的 {@code ClientInformation}，
     * {@link ConnectionContext#getTranslation} 会回退到代理默认语言。
     *
     * @param ctx     上游连接的上下文
     * @param proxy   代理实例
     * @param profile 被判为重复的档案
     */
    private static void denyDuplicateLogin(ChannelHandlerContext ctx, StarlightProxy proxy, GameProfile profile) {
        ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();

        log.info(proxy.getTranslateManager().translate(
                        "starlight.logging.info.player.duplicate_login"),
                profile.username(), profile.uuid(), ctx.channel().remoteAddress());

        Component reason = MiniMessageUtils.MINI_MESSAGE.deserialize(
                context == null
                        ? proxy.getTranslateManager().translate(
                                proxy.getTranslateManager().getActiveLocale(),
                                "starlight.disconnect.already_connected")
                        : context.getTranslation("starlight.disconnect.already_connected"));

        ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(reason))
                .addListener(_ -> ctx.channel().close());
    }

    private static void sendLoginSuccess(ChannelHandlerContext ctx, ProxiedPlayer player) {
        ctx.channel().writeAndFlush(new ClientboundLoginSuccessPacket(player.getGameProfile(), UUID.randomUUID())).addListener(_ -> {
            player.getConnectionContext().setOutboundState(ProtocolState.CONFIGURATION);
            log.debug("上游Outbound切换到CONFIGURATION");
        });
    }
}

