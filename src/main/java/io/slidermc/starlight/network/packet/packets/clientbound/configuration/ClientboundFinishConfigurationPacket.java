package io.slidermc.starlight.network.packet.packets.clientbound.configuration;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientboundFinishConfigurationPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundFinishConfigurationPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ClientboundFinishConfigurationPacket> {
        @Override
        public void handle(ClientboundFinishConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            DownstreamConnectionContext context = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            StarlightMinecraftClient client = context.getClient();
            client.setInboundState(ProtocolState.PLAY);
            log.debug("下游Inbound设置为PLAY [1]");
            client.getPlayerChannel().writeAndFlush(packet).addListener(_ -> {
                ConnectionContext context1 = client.getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
                context1.setOutboundState(ProtocolState.PLAY);
                log.debug("上游Outbound设置为PLAY [2]");
            });
        }
    }
}
