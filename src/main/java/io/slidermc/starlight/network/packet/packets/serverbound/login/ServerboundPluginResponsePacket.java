package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ServerboundPluginResponsePacket implements IMinecraftPacket {
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
            // TODO: 暂时用不到
        }
    }
}
