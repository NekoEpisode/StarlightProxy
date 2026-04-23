package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.PlayerChatEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundChatPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundChatPacket.class);
    private String message;
    private long timestamp;
    private long salt;
    private byte[] signature; // 256 bytes when present
    private int messageCount;
    private byte[] acknowledged; // Fixed BitSet (20 bits = 3 bytes)
    private byte checksum;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, message);
        byteBuf.writeLong(timestamp);
        byteBuf.writeLong(salt);

        if (signature != null && signature.length == 256) {
            byteBuf.writeBoolean(true);
            byteBuf.writeBytes(signature);
        } else {
            byteBuf.writeBoolean(false);
        }

        MinecraftCodecUtils.writeVarInt(byteBuf, messageCount);
        byteBuf.writeBytes(acknowledged);
        byteBuf.writeByte(checksum);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.message = MinecraftCodecUtils.readString(byteBuf);
        this.timestamp = byteBuf.readLong();
        this.salt = byteBuf.readLong();

        // 读取可选签名 (Prefixed Optional Byte Array)
        boolean hasSignature = byteBuf.readBoolean();
        if (hasSignature) {
            this.signature = new byte[256];
            byteBuf.readBytes(this.signature);
        } else {
            this.signature = null;
        }

        this.messageCount = MinecraftCodecUtils.readVarInt(byteBuf);

        // Fixed BitSet (20 bits = 3 bytes)
        this.acknowledged = new byte[3];
        byteBuf.readBytes(this.acknowledged);

        // Checksum
        this.checksum = byteBuf.readByte();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
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

    public static class Listener implements IPacketListener<ServerboundChatPacket> {
        @Override
        public void handle(ServerboundChatPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ProxiedPlayer player = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get().getPlayer();
            PlayerChatEvent event = new PlayerChatEvent(player, packet.message);
            proxy.getEventManager().fireAsync(event).whenComplete((chatEvent, throwable) -> {
                if (throwable != null) {
                    log.warn("PlayerChatEvent dispatch failed for {}", player.getGameProfile().username(), throwable); // TODO: 改为使用翻译
                    ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get().toDownstream(packet);
                    return;
                }

                if (!chatEvent.isCancelled()) {
                    String message = packet.message;

                    if (!message.equals(chatEvent.getMessage())) {
                        // 由于聊天签名机制，修改消息内容会导致签名验证失败
                        // 在 online-mode 服务器上，这可能导致玩家被踢出
                        log.debug("Chat message from {} was modified: {} -> {}", player.getGameProfile().username(), message, event.getMessage());
                        packet.message = event.getMessage();
                        packet.signature = null; // 清除签名，因为消息已被修改
                    }

                    ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get().toDownstream(packet);
                }
            });
        }
    }
}
