package io.slidermc.starlight.network.packet.packets.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.NBTHelper;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.DisconnectUtils;
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
            // 转发断开包给玩家并关闭上游 channel
            DisconnectUtils.forwardAndClose(client, packet.getReason(), proxy);
            // 关闭下游 channel
            client.disconnect();
        }
    }
}
