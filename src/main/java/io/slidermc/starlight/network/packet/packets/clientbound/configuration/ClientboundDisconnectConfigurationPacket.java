package io.slidermc.starlight.network.packet.packets.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.NBTHelper;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;

public class ClientboundDisconnectConfigurationPacket implements IMinecraftPacket {
    private Component reason;

    public ClientboundDisconnectConfigurationPacket() {}

    public ClientboundDisconnectConfigurationPacket(Component reason) {
        this.reason = reason;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        NBTHelper.writeComponent(byteBuf, reason);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.reason = NBTHelper.readComponent(byteBuf);
    }

    public Component getReason() {
        return reason;
    }

    public void setReason(Component reason) {
        this.reason = reason;
    }

    public static class Listener implements IPacketListener<ClientboundDisconnectConfigurationPacket> {
        @Override
        public void handle(ClientboundDisconnectConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            StarlightMinecraftClient client = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient();
            ConnectionContext context = client.getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            if (context.getOutboundState() == ProtocolState.LOGIN) {
                client.getPlayerChannel().writeAndFlush(new ClientboundDisconnectLoginPacket(packet.getReason()));
            } else if (context.getOutboundState() == ProtocolState.CONFIGURATION) {
                client.getPlayerChannel().writeAndFlush(new ClientboundDisconnectConfigurationPacket(packet.getReason()));
            }
            // TODO: 实现其他两个状态
        }
    }
}
