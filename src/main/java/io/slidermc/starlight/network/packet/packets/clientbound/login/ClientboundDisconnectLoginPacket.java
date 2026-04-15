package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class ClientboundDisconnectLoginPacket implements IMinecraftPacket {
    private static final GsonComponentSerializer serializer = GsonComponentSerializer.gson();

    private Component reason;

    public ClientboundDisconnectLoginPacket() {}

    public ClientboundDisconnectLoginPacket(Component reason) {
        this.reason = reason;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        String json = serializer.serialize(reason);
        MinecraftCodecUtils.writeString(byteBuf, json);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        String json = MinecraftCodecUtils.readString(byteBuf);
        this.reason = serializer.deserialize(json);
    }

    public Component getReason() {
        return reason;
    }

    public void setReason(Component reason) {
        this.reason = reason;
    }

    public static class Listener implements IPacketListener<ClientboundDisconnectLoginPacket> {
        @Override
        public void handle(ClientboundDisconnectLoginPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            StarlightMinecraftClient client = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient();
            io.netty.channel.Channel playerChannel = client.getPlayerChannel();
            if (playerChannel != null && playerChannel.isActive()) {
                ConnectionContext context = playerChannel.attr(AttributeKeys.CONNECTION_CONTEXT).get();
                if (context.getOutboundState() == ProtocolState.LOGIN) {
                    playerChannel.writeAndFlush(new ClientboundDisconnectLoginPacket(packet.getReason()));
                } else if (context.getOutboundState() == ProtocolState.CONFIGURATION) {
                    playerChannel.writeAndFlush(new ClientboundDisconnectConfigurationPacket(packet.getReason()));
                }
                // TODO: 实现其他两个状态
            }
            // 显式完成 loginFuture，触发上游 cleanup（恢复 autoRead、关闭 upstream channel）
            client.callLoginCompleteExceptionally(new RuntimeException("Downstream disconnected during login: " + packet.getReason()));
        }
    }
}
