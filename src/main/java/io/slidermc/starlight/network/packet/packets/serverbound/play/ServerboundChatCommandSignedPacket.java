package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

import java.util.ArrayList;
import java.util.List;

public class ServerboundChatCommandSignedPacket implements IMinecraftPacket {
    private static final int SIGNATURE_LENGTH = 256;
    private static final int ACKNOWLEDGED_LENGTH = 3;

    private String command;
    private long timestamp;
    private long salt;
    private List<ArgumentSignature> argumentSignatures = List.of();
    private int messageCount;
    private byte[] acknowledged;
    private byte checksum;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, command);
        byteBuf.writeLong(timestamp);
        byteBuf.writeLong(salt);

        MinecraftCodecUtils.writeVarInt(byteBuf, argumentSignatures.size());
        for (ArgumentSignature signature : argumentSignatures) {
            MinecraftCodecUtils.writeString(byteBuf, signature.name());
            byteBuf.writeBytes(signature.signature());
        }

        MinecraftCodecUtils.writeVarInt(byteBuf, messageCount);
        byteBuf.writeBytes(acknowledged);
        byteBuf.writeByte(checksum);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.command = MinecraftCodecUtils.readString(byteBuf);
        this.timestamp = byteBuf.readLong();
        this.salt = byteBuf.readLong();

        int signatureCount = MinecraftCodecUtils.readVarInt(byteBuf);
        List<ArgumentSignature> signatures = new ArrayList<>(signatureCount);
        for (int i = 0; i < signatureCount; i++) {
            String name = MinecraftCodecUtils.readString(byteBuf);
            // 签名是定长 256 字节，没有长度前缀
            byte[] signature = new byte[SIGNATURE_LENGTH];
            byteBuf.readBytes(signature);
            signatures.add(new ArgumentSignature(name, signature));
        }
        this.argumentSignatures = signatures;

        this.messageCount = MinecraftCodecUtils.readVarInt(byteBuf);
        this.acknowledged = new byte[ACKNOWLEDGED_LENGTH];
        byteBuf.readBytes(this.acknowledged);
        this.checksum = byteBuf.readByte();
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSalt() {
        return salt;
    }

    public void setSalt(long salt) {
        this.salt = salt;
    }

    public List<ArgumentSignature> getArgumentSignatures() {
        return argumentSignatures;
    }

    public void setArgumentSignatures(List<ArgumentSignature> argumentSignatures) {
        this.argumentSignatures = argumentSignatures;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public byte[] getAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(byte[] acknowledged) {
        this.acknowledged = acknowledged;
    }

    public byte getChecksum() {
        return checksum;
    }

    public void setChecksum(byte checksum) {
        this.checksum = checksum;
    }

    public record ArgumentSignature(String name, byte[] signature) {}

    public static class Listener implements IPacketListener<ServerboundChatCommandSignedPacket> {
        @Override
        public void handle(ServerboundChatCommandSignedPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();

            // 命中代理命令时由管理器自行执行；未命中或玩家无权则原样转发到下游
            if (proxy.getCommandManager().dispatch(packet.getCommand(), context.getPlayer())) {
                return;
            }
            context.getDownstreamChannel().writeAndFlush(packet);
        }
    }
}
