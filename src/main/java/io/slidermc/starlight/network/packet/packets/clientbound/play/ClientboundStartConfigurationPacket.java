package io.slidermc.starlight.network.packet.packets.clientbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ClientboundStartConfigurationPacket implements IMinecraftPacket {
    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ClientboundStartConfigurationPacket> {
        @Override
        public void handle(ClientboundStartConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            DownstreamConnectionContext context = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            StarlightMinecraftClient client = context.getClient();
            // Downstream has switched to CONFIGURATION outbound; update inbound state immediately
            // so ClientPacketDecoder can recognise FinishConfiguration when it arrives.
            client.setInboundState(ProtocolState.CONFIGURATION);
            client.getPlayerChannel().writeAndFlush(packet).addListener(_ -> {
                ConnectionContext connectionContext = client.getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
                connectionContext.setOutboundState(ProtocolState.CONFIGURATION);
            });
        }
    }
}
