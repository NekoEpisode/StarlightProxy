package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;

import java.util.zip.Deflater;

/**
 * Minecraft 压缩编码器（pipeline 中位于 PacketEncoder 之后）。
 *
 * <p>输入是上游 PacketEncoder 写出的普通帧 {@code [length VarInt][packetId VarInt + payload]}，
 * 先用 length 切出包体，再输出压缩格式帧：
 * <pre>
 *   [Packet Length : VarInt]  — (Data Length VarInt 的字节数) + 压缩/原始数据的字节数
 *   [Data Length   : VarInt]  — 0 = 未压缩; >0 = 解压后的字节数
 *   [Data          : bytes ]  — 若 Data Length>0 则为 zlib 压缩数据，否则为原始数据
 * </pre>
 * 当 {@code packetId + payload} 的字节数 {@code >= threshold} 时才压缩。
 */
public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int threshold;
    private final Deflater deflater;

    /**
     * @param threshold 压缩阈值（字节数）。{@code packetId + payload} 大于等于此值时启用压缩。
     * @param level     zlib 压缩级别，{@link Deflater#DEFAULT_COMPRESSION} 是通常选择。
     */
    public CompressionEncoder(int threshold, int level) {
        this.threshold = threshold;
        this.deflater = new Deflater(level);
    }

    /** 使用默认压缩级别。 */
    public CompressionEncoder(int threshold) {
        this(threshold, Deflater.DEFAULT_COMPRESSION);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf frame, ByteBuf out) throws Exception {
        // 上游 PacketEncoder 写出的是 [length VarInt][packetId + payload]，这里用 length 切出包体
        int contentLength = MinecraftCodecUtils.readVarInt(frame);
        if (frame.readableBytes() < contentLength) {
            throw new DecoderException("Frame length " + contentLength
                    + " exceeds available bytes " + frame.readableBytes());
        }
        ByteBuf content = frame.readSlice(contentLength);

        if (contentLength < threshold) {
            MinecraftCodecUtils.writeVarInt(out, MinecraftCodecUtils.varIntSize(0) + contentLength);
            MinecraftCodecUtils.writeVarInt(out, 0);
            out.writeBytes(content);
            return;
        }

        byte[] input = new byte[contentLength];
        content.readBytes(input);
        deflater.setInput(input);
        deflater.finish();

        ByteBuf compressed = ctx.alloc().buffer();
        try {
            byte[] temp = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(temp);
                compressed.writeBytes(temp, 0, count);
            }
            deflater.reset();

            int compressedLen = compressed.readableBytes();
            MinecraftCodecUtils.writeVarInt(out, MinecraftCodecUtils.varIntSize(contentLength) + compressedLen);
            MinecraftCodecUtils.writeVarInt(out, contentLength);
            out.writeBytes(compressed);
        } finally {
            compressed.release();
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        deflater.end();
        super.handlerRemoved(ctx);
    }
}
