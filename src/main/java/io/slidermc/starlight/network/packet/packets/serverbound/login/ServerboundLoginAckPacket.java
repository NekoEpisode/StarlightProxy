package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.LoginResult;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundLoginAckPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundLoginAckPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ServerboundLoginAckPacket> {
        @Override
        public void handle(ServerboundLoginAckPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            log.debug("LoginAcknowledge, 上游Inbound切换到CONFIGURATION");
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.setInboundState(ProtocolState.CONFIGURATION);

            // 暂停玩家 channel 的读取：客户端进入 CONFIGURATION 后立刻开始发包，
            // 但下游连接是异步的，必须等下游 login 完成后再恢复，否则包会丢失。
            // Netty 会把这期间到达的数据留在 TCP buffer 里，恢复后自动重新处理。
            ctx.channel().config().setAutoRead(false);

            StarlightMinecraftClient client = new StarlightMinecraftClient(
                    proxy.getServerManager().getDefaultServer().getAddress(),
                    proxy.getRegistryPacketUtils().getPacketRegistry(),
                    proxy
            );
            try {
                client.connectAsync().whenComplete((_, connectThrowable) -> {
                    if (connectThrowable != null) {
                        log.error(proxy.getTranslateManager().translate("starlight.logging.error.connect_default_server_failed"), connectThrowable);
                        ctx.channel().config().setAutoRead(true);
                        kickWithConfigDisconnect(ctx, buildConnectFailedMessage(proxy, context.getLocale()));
                        return;
                    }
                    // 必须在 login() 之前设置 playerChannel，否则 login 阶段
                    // 收到 LoginDisconnect 时无法获取上游 channel
                    client.setPlayerChannel(ctx.channel());
                    client.login(
                            context.getHandshakeInformation().getProtocolVersion(),
                            context.getPlayer().getGameProfile().username(),
                            context.getPlayer().getGameProfile().uuid()
                    ).whenComplete((result, loginThrowable) -> {
                        if (loginThrowable != null) {
                            log.error(proxy.getTranslateManager().translate("starlight.logging.error.downstream_login_failed"), loginThrowable);
                            ctx.channel().config().setAutoRead(true);
                            Component reason = MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    context.getTranslation("starlight.disconnect.login_failed"),
                                    Placeholder.parsed("error_msg",
                                            loginThrowable.getMessage() != null ? loginThrowable.getMessage()
                                                    : context.getTranslation("starlight.unknown_error"))
                            );
                            kickWithConfigDisconnect(ctx, reason);
                            return;
                        }
                        switch (result) {
                            case LoginResult.Success() -> {
                                log.debug("设置上游和下游的连接");
                                context.setDownstreamChannel(client.getChannel());
                                context.getPlayer().setCurrentServer(proxy.getServerManager().getDefaultServer());
                                context.getPlayer().setPreviousServer(null);
                                // 下游登录完成，恢复读取，之前 buffer 的 CONFIGURATION 包现在开始转发
                                ctx.channel().config().setAutoRead(true);
                            }
                            case LoginResult.Kicked(Component reason) -> {
                                log.warn(proxy.getTranslateManager().translate("starlight.logging.error.downstream_login_failed"),
                                        reason);
                                ctx.channel().config().setAutoRead(true);
                                kickWithConfigDisconnect(ctx, reason);
                            }
                            case LoginResult.Error(Throwable cause) -> {
                                log.error(proxy.getTranslateManager().translate("starlight.logging.error.downstream_login_failed"),
                                        cause);
                                ctx.channel().config().setAutoRead(true);
                                Component reason = MiniMessageUtils.MINI_MESSAGE.deserialize(
                                        context.getTranslation("starlight.disconnect.login_failed"),
                                        Placeholder.parsed("error_msg",
                                                cause.getMessage() != null ? cause.getMessage()
                                                        : context.getTranslation("starlight.unknown_error"))
                                );
                                kickWithConfigDisconnect(ctx, reason);
                            }
                        }
                    });
                });
            } catch (Exception e) {
                log.error(proxy.getTranslateManager().translate("starlight.logging.error.connect_default_server_failed"), e);
                ctx.channel().config().setAutoRead(true);
                kickWithConfigDisconnect(ctx, buildConnectFailedMessage(proxy, context.getLocale()));
            }
        }

        private static Component buildConnectFailedMessage(StarlightProxy proxy, String locale) {
            String serverName = proxy.getServerManager().getDefaultServer().getName();

            String template = proxy.getTranslateManager().translate(locale, "starlight.disconnect.failed_connect_default_server");

            return MiniMessageUtils.MINI_MESSAGE.deserialize(
                    template,
                    Placeholder.parsed("server_name", serverName)
            );
        }

        /**
         * 向处于 CONFIGURATION 状态的玩家发送断开包并关闭连接。
         */
        private static void kickWithConfigDisconnect(ChannelHandlerContext ctx, Component reason) {
            ctx.channel().writeAndFlush(new ClientboundDisconnectConfigurationPacket(reason))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
