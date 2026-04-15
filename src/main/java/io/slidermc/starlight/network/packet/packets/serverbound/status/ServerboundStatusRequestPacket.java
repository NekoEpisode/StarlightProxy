package io.slidermc.starlight.network.packet.packets.serverbound.status;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ServerboundStatusRequestPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundStatusRequestPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ServerboundStatusRequestPacket> {
        @Override
        public void handle(ServerboundStatusRequestPacket packet, ChannelHandlerContext ctx) {
            log.debug("收到StatusRequest包");
            ctx.channel().writeAndFlush(new ClientboundStatusResponsePacket(
                    "Starlight 26.1",
                    775,
                    100,
                    0,
                    List.of(),
                    Component.text("Hello World!"),
                    null,
                    false
            ));
        }
    }
}
