package io.slidermc.starlight.network.packet.packets.serverbound.login.helper;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.PlayerLoginEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.codec.CompressionDecoder;
import io.slidermc.starlight.network.codec.CompressionEncoder;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundLoginSuccessPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundSetCompressionPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @param ctx     上游客户端的 ChannelHandlerContext
     * @param proxy   代理实例
     * @param profile 已确定的 GameProfile（离线或正版均可）
     */
    public static void completeLogin(ChannelHandlerContext ctx, StarlightProxy proxy, GameProfile profile) {
        ProxiedPlayer player = new ProxiedPlayer(profile, ctx.channel(), proxy, true);
        log.debug("已创建ProxiedPlayer对象: {}", player);
        player.getConnectionContext().setPlayer(player);
        proxy.getPlayerManager().addPlayer(player);
        PlayerLoginEvent playerLoginEvent = new PlayerLoginEvent(player);
        proxy.getEventManager().fireAsync(playerLoginEvent).thenRun(() -> { // 异步call, 防止拖慢Netty
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
                        if (ctx.pipeline().get(InternalConfig.HANDLER_DECOMPRESS) == null) {
                            ctx.pipeline().addBefore(InternalConfig.HANDLER_DECODER, InternalConfig.HANDLER_DECOMPRESS, new CompressionDecoder());
                        }
                        if (ctx.pipeline().get(InternalConfig.HANDLER_COMPRESS) == null) {
                            ctx.pipeline().addBefore(InternalConfig.HANDLER_ENCODER, InternalConfig.HANDLER_COMPRESS, new CompressionEncoder(threshold));
                        }
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

    private static void sendLoginSuccess(ChannelHandlerContext ctx, ProxiedPlayer player) {
        ctx.channel().writeAndFlush(new ClientboundLoginSuccessPacket(player.getGameProfile())).addListener(_ -> {
            player.getConnectionContext().setOutboundState(ProtocolState.CONFIGURATION);
            log.debug("上游Outbound切换到CONFIGURATION");
        });
    }
}

