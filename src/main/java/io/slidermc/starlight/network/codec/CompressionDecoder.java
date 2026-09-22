package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Minecraft 压缩解码器（pipeline 中位于 FrameDecoder 之后、PacketDecoder 之前）。
 *
 * <p>从 {@link FrameDecoder} 拿到的是不含 length 的帧体，格式为：
 * <pre>
 *   [Data Length : VarInt]  — 0 = 未压缩; >0 = 解压后的字节数
 *   [Data        : bytes ]  — 若 Data Length>0 则为 zlib 压缩数据，否则为原始数据
 * </pre>
 * 解压后直接向下游输出裸字节 {@code [packetId VarInt + payload]}，不再添加任何长度前缀。
 */
public class CompressionDecoder extends ByteToMessageDecoder {

    private final Inflater inflater = new Inflater();
    private boolean isInflaterClosed = false;

    public CompressionDecoder() {}

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int dataLength = MinecraftCodecUtils.readVarInt(in);

        if (dataLength == 0) {
            int rawLen = in.readableBytes();
            if (rawLen > InternalConfig.MAX_UNCOMPRESSED_SIZE) {
                throw new DecoderException("Uncompressed data length " + rawLen +
                        " exceeds maximum " + InternalConfig.MAX_UNCOMPRESSED_SIZE);
            }
            out.add(in.readBytes(rawLen));
            return;
        }

        if (dataLength > InternalConfig.MAX_UNCOMPRESSED_SIZE) {
            throw new DecoderException("Uncompressed data length " + dataLength
                    + " exceeds maximum " + InternalConfig.MAX_UNCOMPRESSED_SIZE);
        }

        byte[] compressedBytes = new byte[in.readableBytes()];
        in.readBytes(compressedBytes);

        inflater.setInput(compressedBytes);
        byte[] decompressed = new byte[dataLength];
        try {
            int actual = inflater.inflate(decompressed);
            if (actual != dataLength || !inflater.finished() || inflater.getRemaining() != 0) {
                throw new DecoderException("Decompressed size mismatch: expected "
                        + dataLength + ", got " + actual);
            }
        } catch (DataFormatException e) {
            throw new DecoderException("Failed to decompress packet", e);
        } finally {
            inflater.reset();
        }

        out.add(ctx.alloc().buffer(dataLength).writeBytes(decompressed));
    }
    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        if (!isInflaterClosed) {
            isInflaterClosed = true;
            inflater.end();
        }
        super.handlerRemoved0(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!isInflaterClosed) {
            isInflaterClosed = true;
            inflater.end();
        }
        super.channelInactive(ctx);
    }
}
