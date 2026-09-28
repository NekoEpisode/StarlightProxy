package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundPluginResponsePacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundPluginResponsePacket.class);

    private int messageId;
    private boolean hasData;
    private byte[] data;

    public ServerboundPluginResponsePacket() {}

    public ServerboundPluginResponsePacket(int messageId, boolean hasData, byte[] data) {
        this.messageId = messageId;
        this.data = data;
        this.hasData = hasData;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, messageId);
        byteBuf.writeBoolean(hasData);
        if (hasData) {
            byteBuf.writeBytes(data);
        }
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.messageId = MinecraftCodecUtils.readVarInt(byteBuf);
        this.hasData = byteBuf.readBoolean();
        if (hasData) {
            this.data = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(this.data);
        }
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isHasData() {
        return hasData;
    }

    public void setHasData(boolean hasData) {
        this.hasData = hasData;
    }

    public static class Listener implements IPacketListener<ServerboundPluginResponsePacket> {
        @Override
        public void handle(ServerboundPluginResponsePacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            if (context == null) {
                log.debug("收到登录插件应答但没有连接上下文，已忽略: id={}", packet.messageId);
                return;
            }

            // 按消息 ID 路由回发起查询的一方；未知 ID 会被管理器忽略
            context.getLoginQueries().complete(packet.messageId, packet.hasData, packet.data);
        }
    }
}
