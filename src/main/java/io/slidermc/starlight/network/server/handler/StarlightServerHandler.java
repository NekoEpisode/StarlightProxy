package io.slidermc.starlight.network.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
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
        ProxiedPlayer player = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get().getPlayer();
        if (player != null) {
            proxy.getPlayerManager().removePlayer(player.getGameProfile().uuid());
            log.info("Player {} exited", player.getGameProfile().username());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof IMinecraftPacket packet) {
            packetRegistry.dispatch(packet, ctx, proxy);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
