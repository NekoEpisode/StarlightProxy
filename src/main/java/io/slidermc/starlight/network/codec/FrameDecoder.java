package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * Minecraft 帧解码器（pipeline 中位于最前，早于 decrypt/compress）。
 *
 * <p>读取 {@code [Packet Length : VarInt]} 并向下游输出恰好该长度的完整帧，
 * 之后所有 handler 处理的都是不含 length 前缀的包体。
 */
public class FrameDecoder extends ByteToMessageDecoder {
    private static final int MAX_VARINT21_BYTES = 3;
    private final ByteBuf helperBuf = Unpooled.directBuffer(MAX_VARINT21_BYTES);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        helperBuf.clear();

        if (!copyVarint(in, helperBuf)) {
            in.resetReaderIndex();
            return;
        }

        int length = readVarInt(helperBuf);
        if (length == 0) {
            throw new CorruptedFrameException("Frame length cannot be zero");
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(in.readBytes(length));
    }

    /**
     * 逐字节把 length VarInt 复制到 {@code out}；数据不足时返回 false（调用方应 reset）。
     */
    private static boolean copyVarint(ByteBuf in, ByteBuf out) {
        for (int i = 0; i < MAX_VARINT21_BYTES; i++) {
            if (!in.isReadable()) {
                return false;
            }
            byte b = in.readByte();
            out.writeByte(b);
            if ((b & 0x80) == 0) {
                return true;
            }
        }
        throw new CorruptedFrameException("length wider than 21-bit");
    }

    private static int readVarInt(ByteBuf buf) {
        int result = 0;
        for (int i = 0; i < MAX_VARINT21_BYTES; i++) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return result;
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) {
        helperBuf.release();
    }
}
