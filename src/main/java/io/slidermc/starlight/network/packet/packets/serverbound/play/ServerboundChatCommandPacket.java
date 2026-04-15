package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ServerboundChatCommandPacket implements IMinecraftPacket {
    private String command;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, command);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.command = MinecraftCodecUtils.readString(byteBuf);
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public static class Listener implements IPacketListener<ServerboundChatCommandPacket> {
        @Override
        public void handle(ServerboundChatCommandPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            if (packet.command.startsWith("server")) {
                String[] parts = packet.command.split(" ");
                if (parts.length == 2) {
                    String server = parts[1];
                    ProxiedServer server1 = proxy.getServerManager().getServer(server);
                    if (server1 != null) {
                        ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get().getPlayer().connect(server1);
                    }
                }
            }
        }
    }
}
