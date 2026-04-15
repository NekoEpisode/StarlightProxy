package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

import java.util.concurrent.CompletableFuture;

public class ServerboundConfigurationAckPacket implements IMinecraftPacket {
    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ServerboundConfigurationAckPacket> {
        @Override
        public void handle(ServerboundConfigurationAckPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.setInboundState(ProtocolState.CONFIGURATION);

            CompletableFuture<Void> pending = context.getPendingReconfiguration();
            if (pending != null) {
                // Proxy-initiated switch (ModernServerSwitcher): complete the future and let the switcher take over.
                context.setPendingReconfiguration(null);
                context.setOutboundState(ProtocolState.CONFIGURATION);
                pending.complete(null);
            } else {
                // Server-initiated reconfiguration: forward the Ack to the current downstream.
                // outboundState for upstream was already set by ClientboundStartConfigurationPacket.Listener.
                Channel downstream = context.getDownstreamChannel();
                if (downstream != null && downstream.isActive()) {
                    DownstreamConnectionContext downstreamCtx = downstream.attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
                    downstream.writeAndFlush(packet).addListener(_ ->
                            downstreamCtx.getClient().setOutboundState(ProtocolState.CONFIGURATION));
                }
            }
        }
    }
}
