package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import javax.crypto.Cipher;

/**
 * AES/CFB8 加密 Handler，安装在 PacketEncoder 之后。
 * 接收已序列化的帧字节，加密后写出。
 */
public class EncryptionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final Cipher cipher;

    public EncryptionEncoder(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int readable = msg.readableBytes();
        byte[] plain = new byte[readable];
        msg.readBytes(plain);

        byte[] encrypted = cipher.update(plain);
        if (encrypted != null && encrypted.length > 0) {
            out.writeBytes(encrypted);
        }
    }
}

