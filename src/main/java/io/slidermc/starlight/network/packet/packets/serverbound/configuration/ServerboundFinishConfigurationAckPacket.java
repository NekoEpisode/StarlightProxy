package io.slidermc.starlight.network.packet.packets.serverbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.PlayerServerConnectedEvent;
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

public class ServerboundFinishConfigurationAckPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundFinishConfigurationAckPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ServerboundFinishConfigurationAckPacket> {
        @Override
        public void handle(ServerboundFinishConfigurationAckPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            context.setInboundState(ProtocolState.PLAY);
            log.debug("上游Inbound设置为PLAY [3]");
            context.getDownstreamChannel().writeAndFlush(packet).addListener(_ -> {
                DownstreamConnectionContext downstreamConnectionContext = context.getDownstreamChannel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
                StarlightMinecraftClient client = downstreamConnectionContext.getClient();
                client.setOutboundState(ProtocolState.PLAY);
                log.debug("下游Outbound设置为PLAY [4]");

                PlayerServerConnectedEvent event = new PlayerServerConnectedEvent(context.getPlayer(), context.getPlayer().getPreviousServer().orElse(null), context.getPlayer().getCurrentServer().orElse(null));
                proxy.getEventManager().fireAsync(event);
            });
        }
    }
}
