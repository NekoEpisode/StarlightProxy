package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
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
        }
    }
}
