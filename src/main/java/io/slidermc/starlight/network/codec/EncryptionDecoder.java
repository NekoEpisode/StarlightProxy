package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import javax.crypto.Cipher;
import java.util.List;

/**
 * AES/CFB8 解密 Handler，安装在 PacketDecoder 之前。
 * 收到原始字节后先解密，再交给下游 Decoder 处理。
 */
public class EncryptionDecoder extends ByteToMessageDecoder {

    private final Cipher cipher;

    public EncryptionDecoder(Cipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int readable = in.readableBytes();
        if (readable == 0) return;

        byte[] encrypted = new byte[readable];
        in.readBytes(encrypted);

        byte[] decrypted = cipher.update(encrypted);
        if (decrypted != null && decrypted.length > 0) {
            out.add(ctx.alloc().buffer(decrypted.length).writeBytes(decrypted));
        }
    }
}

