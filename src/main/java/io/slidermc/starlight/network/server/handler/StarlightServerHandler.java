package io.slidermc.starlight.network.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.PlayerExitEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RawPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarlightServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(StarlightServerHandler.class);
    private final PacketRegistry packetRegistry;
    private final StarlightProxy proxy;

    public StarlightServerHandler(PacketRegistry packetRegistry, StarlightProxy proxy) {
        this.packetRegistry = packetRegistry;
        this.proxy = proxy;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("新连接: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("连接断开: {}", ctx.channel().remoteAddress());
        ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
        if (context != null) {
            ProxiedPlayer player = context.getPlayer();
            if (player != null) {
                proxy.getPlayerManager().removePlayer(player.getGameProfile().uuid());
                player.setOnline(false);
                Channel downstream = player.getConnectionContext().getDownstreamChannel();
                if (downstream != null) {
                    downstream.close();
                }
                log.info(proxy.getTranslateManager().translate("starlight.logging.info.player.exit"), player.getGameProfile().username(), player.getGameProfile().uuid());
                PlayerExitEvent playerExitEvent = new PlayerExitEvent(player);
                proxy.getEventManager().fireAsync(playerExitEvent);
            }
        }
        ctx.channel().close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RawPacket rawPacket) {
            // 透明转发：直接写到下游服务器 channel（经由 ClientPacketEncoder）
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            io.netty.channel.Channel downstream = context != null ? context.getDownstreamChannel() : null;
            if (downstream != null && downstream.isActive()) {
                downstream.writeAndFlush(rawPacket);
            } else {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.packet.received_raw_but_no_downstream_channel"));
            }
        } else if (msg instanceof IMinecraftPacket packet) {
            packetRegistry.dispatch(packet, ctx, proxy);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error(proxy.getTranslateManager().translate("starlight.logging.error.error_on_upstream"), cause);
    }
}
