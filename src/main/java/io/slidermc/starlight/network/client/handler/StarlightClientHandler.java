package io.slidermc.starlight.network.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RawPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarlightClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(StarlightClientHandler.class);
    private final PacketRegistry packetRegistry;
    private final StarlightProxy proxy;
    private final StarlightMinecraftClient client;

    public StarlightClientHandler(PacketRegistry packetRegistry, StarlightProxy proxy, StarlightMinecraftClient client) {
        this.packetRegistry = packetRegistry;
        this.proxy = proxy;
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (client.isLoggingIn()) {
            client.callLoginCompleteExceptionally(new RuntimeException("Disconnected from server during login"));
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RawPacket rawPacket) {
            // 透明转发：直接写到上游玩家 channel（经由 ServerPacketEncoder）
            io.netty.channel.Channel playerChannel = client.getPlayerChannel();
            if (playerChannel != null && playerChannel.isActive()) {
                playerChannel.writeAndFlush(rawPacket);
            } else {
                log.warn("Received RawPacket from downstream but no active player channel, dropping");
            }
        } else if (msg instanceof IMinecraftPacket packet) {
            packetRegistry.dispatch(packet, ctx, proxy);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (client.isLoggingIn()) {
            client.callLoginCompleteExceptionally(cause);
            client.disconnect();
            log.debug("Exceptionally disconnected from server", cause);
        }
        log.error("下游连接出现错误！", cause);
    }
}
