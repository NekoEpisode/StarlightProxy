package io.slidermc.starlight.network.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;

import java.util.zip.Deflater;

/**
 * Minecraft 压缩格式编码器（放在 PacketEncoder 之后，或在其前面拦截已序列化的帧）。
 *
 * <p>本 handler 接收已序列化的普通帧 {@code [length VarInt][packetId VarInt + payload]}，
 * 将其转换为压缩格式：
 * <pre>
 *   [Packet Length : VarInt]  — (Data Length VarInt 大小) + 压缩/原始数据大小
 *   [Data Length   : VarInt]  — 0 = 未压缩; >0 = 解压后的字节数（即原始 packetId+payload 大小）
 *   [Data          : bytes ]  — 若压缩则为 zlib 数据，否则为原始数据
 * </pre>
 *
 * <p>当 {@code packetId + payload} 的字节数 {@code >= threshold} 时压缩，否则写 {@code Data Length = 0}。
 *
 * <p><b>注意：</b>本 handler 操作的是已由上游 Encoder 写好的 <em>完整帧</em>（ByteBuf），
 * 因此需要接在 PacketEncoder 之后，以 {@link ByteBuf} 而非 {@link io.slidermc.starlight.network.packet.IMinecraftPacket}
 * 为输入类型。若你想把它插在 PacketEncoder 之前，需改为操作 IMinecraftPacket 并自行序列化。
 */
public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int threshold;
    private final Deflater deflater;

    /**
     * @param threshold 压缩阈值（字节数）。{@code packetId + payload} 大于等于此值时启用压缩。
     *                  负数表示禁用压缩（此时不应把本 handler 加入 pipeline）。
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

    /**
     * 输入：由上游 PacketEncoder 写好的完整普通帧 {@code [length VarInt][content bytes]}。
     * 输出：压缩格式帧写入 {@code out}。
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf frame, ByteBuf out) throws Exception {
        // 1. 从普通帧里剥离 length 前缀，取出 content（packetId + payload）
        int contentLength = MinecraftCodecUtils.readVarInt(frame);
        byte[] content = new byte[contentLength];
        frame.readBytes(content);

        if (contentLength < threshold) {
            // 不压缩：Data Length = 0
            // Packet Length = varIntSize(0) + contentLength
            int packetLength = MinecraftCodecUtils.varIntSize(0) + contentLength;
            MinecraftCodecUtils.writeVarInt(out, packetLength);
            MinecraftCodecUtils.writeVarInt(out, 0);          // Data Length = 0
            out.writeBytes(content);
        } else {
            // 压缩
            deflater.setInput(content);
            deflater.finish();

            byte[] compressed = new byte[content.length + 256]; // 增加富余量，避免zlib在小数据/不可压数据时截断
            int compressedLen = deflater.deflate(compressed);
            deflater.reset();

            // 如果压缩后反而更大，仍然使用压缩格式（协议规定 >= threshold 必须走压缩格式）
            // Packet Length = varIntSize(dataLength) + compressedLen
            int packetLength = MinecraftCodecUtils.varIntSize(contentLength) + compressedLen;
            MinecraftCodecUtils.writeVarInt(out, packetLength);
            MinecraftCodecUtils.writeVarInt(out, contentLength); // Data Length = 解压后大小
            out.writeBytes(compressed, 0, compressedLen);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        deflater.end();
        super.handlerRemoved(ctx);
    }
}

