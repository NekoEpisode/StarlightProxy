package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ClientboundEncryptionRequestPacket implements IMinecraftPacket {
    private String serverId;
    private byte[] publicKey;
    private byte[] verifyToken;
    private boolean shouldAuthenticate;

    public ClientboundEncryptionRequestPacket() {}

    public ClientboundEncryptionRequestPacket(String serverId, byte[] publicKey, byte[] verifyToken, boolean shouldAuthenticate) {
        this.serverId = serverId;
        this.publicKey = publicKey;
        this.verifyToken = verifyToken;
        this.shouldAuthenticate = shouldAuthenticate;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        if (serverId.length() > 20) throw new IllegalArgumentException("Server ID length cannot be greater than 20");
        MinecraftCodecUtils.writeString(byteBuf, serverId);
        MinecraftCodecUtils.writeByteArray(byteBuf, publicKey);
        MinecraftCodecUtils.writeByteArray(byteBuf, verifyToken);
        byteBuf.writeBoolean(shouldAuthenticate);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        String serverId = MinecraftCodecUtils.readString(byteBuf);
        if (serverId.length() > 20) return;
        this.serverId = serverId;
        this.publicKey = MinecraftCodecUtils.readByteArray(byteBuf);
        this.verifyToken = MinecraftCodecUtils.readByteArray(byteBuf);
        this.shouldAuthenticate = byteBuf.readBoolean();
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public boolean isShouldAuthenticate() {
        return shouldAuthenticate;
    }

    public void setShouldAuthenticate(boolean shouldAuthenticate) {
        this.shouldAuthenticate = shouldAuthenticate;
    }

    public static class Listener implements IPacketListener<ClientboundEncryptionRequestPacket> {
        @Override
        public void handle(ClientboundEncryptionRequestPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // 下游把Encryption发来了，应该是配置错误
            // TODO: 待处理
        }
    }
}
