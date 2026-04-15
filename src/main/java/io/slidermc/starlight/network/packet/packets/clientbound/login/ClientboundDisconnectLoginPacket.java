package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.DisconnectUtils;
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
            // 转发断开包给玩家并关闭上游 channel
            DisconnectUtils.forwardAndClose(client, packet.getReason());
            // 完成 loginFuture（异常），触发 ServerboundLoginAckPacket.Listener 的 whenComplete 清理逻辑
            // 同时关闭下游 channel
            client.callLoginCompleteExceptionally(new RuntimeException("Downstream disconnected during login"));
            client.disconnect();
        }
    }
}
