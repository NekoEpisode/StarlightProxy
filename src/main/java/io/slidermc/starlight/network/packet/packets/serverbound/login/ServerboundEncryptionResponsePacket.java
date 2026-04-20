package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class ServerboundEncryptionResponsePacket implements IMinecraftPacket {
    private byte[] sharedSecret;
    private byte[] verifyToken;

    public ServerboundEncryptionResponsePacket() {}

    public ServerboundEncryptionResponsePacket(byte[] sharedSecret, byte[] verifyToken) {
        this.sharedSecret = sharedSecret;
        this.verifyToken = verifyToken;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeByteArray(byteBuf, sharedSecret);
        MinecraftCodecUtils.writeByteArray(byteBuf, verifyToken);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.sharedSecret = MinecraftCodecUtils.readByteArray(byteBuf);
        this.verifyToken = MinecraftCodecUtils.readByteArray(byteBuf);
    }

    public byte[] getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(byte[] sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public static class Listener implements IPacketListener<ServerboundEncryptionResponsePacket> {
        @Override
        public void handle(ServerboundEncryptionResponsePacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // TODO: 待处理
        }
    }
}
