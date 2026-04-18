package io.slidermc.starlight.network.packet.packets.clientbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.NBTHelper;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientboundSystemChatPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundSystemChatPacket.class);
    private Component component;
    private boolean overlay;

    public ClientboundSystemChatPacket() {}

    public ClientboundSystemChatPacket(Component component, boolean overlay) {
        this.component = component;
        this.overlay = overlay;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        NBTHelper.writeComponent(byteBuf, component); // FIXME: 不符合Minecraft协议标准(len max 262144)，后续更改
        byteBuf.writeBoolean(overlay);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        int readableBytes = byteBuf.readableBytes();
        if (readableBytes > 1) {
            ByteBuf nbtBuf = byteBuf.readSlice(readableBytes - 1);
            this.component = NBTHelper.readComponent(nbtBuf);
        } else {
            this.component = Component.empty();
        }

        this.overlay = byteBuf.readBoolean();
    }

    public Component getComponent() {
        return component;
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    public boolean isOverlay() {
        return overlay;
    }

    public void setOverlay(boolean overlay) {
        this.overlay = overlay;
    }

    public static class Listener implements IPacketListener<ClientboundSystemChatPacket> {
        @Override
        public void handle(ClientboundSystemChatPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // 透明转发
            DownstreamConnectionContext downContext = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            StarlightMinecraftClient client = downContext.getClient();
            client.getPlayerChannel().writeAndFlush(packet);
        }
    }
}
