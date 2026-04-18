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
 * Minecraft 压缩格式解码器（放在 PacketDecoder 之前）。
 *
 * <p>启用压缩后，每个帧的格式为：
 * <pre>
 *   [Packet Length : VarInt]  — (Data Length VarInt 的字节数) + 压缩/原始数据的字节数
 *   [Data Length   : VarInt]  — 0 = 未压缩; >0 = 解压后的字节数
 *   [Data          : bytes ]  — 若 Data Length>0 则为 zlib 压缩数据，否则为原始数据
 * </pre>
 *
 * <p>本 handler 的职责是把上面的帧还原为无压缩格式，即
 * {@code [length VarInt][packetId VarInt + payload]}，
 * 供下游的 PacketDecoder（ServerPacketDecoder / ClientPacketDecoder）直接使用。
 */
public class CompressionDecoder extends ByteToMessageDecoder {

    private final Inflater inflater = new Inflater();
    private boolean isInflaterClosed = false;

    public CompressionDecoder() {}

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 1) return;

        in.markReaderIndex();

        // 1. 读 Packet Length（= Data Length VarInt 大小 + 压缩/原始数据大小）
        int packetLength;
        try {
            packetLength = MinecraftCodecUtils.readVarInt(in);
        } catch (RuntimeException e) {
            in.resetReaderIndex();
            // 不够完整的 VarInt 时，暂不处理
            return;
        }

        if (in.readableBytes() < packetLength) {
            in.resetReaderIndex();
            return;
        }

        // 记录帧结束位置
        int frameEnd = in.readerIndex() + packetLength;

        // 2. 读 Data Length
        int dataLength = MinecraftCodecUtils.readVarInt(in);

        if (dataLength == 0) {
            // 未压缩：直接提取到 frameEnd 的字节
            int rawLen = frameEnd - in.readerIndex();
            if (rawLen < 0) {
                // 不可能出现，除非上游包长度数据极其离谱
                throw new DecoderException("Negative uncompressed raw length: " + rawLen);
            }
            if (rawLen > InternalConfig.MAX_UNCOMPRESSED_SIZE) {
                throw new DecoderException("Uncompressed data length " + rawLen +
                        " exceeds maximum " + InternalConfig.MAX_UNCOMPRESSED_SIZE);
            }
            ByteBuf plain = ctx.alloc().buffer(MinecraftCodecUtils.varIntSize(rawLen) + rawLen);
            MinecraftCodecUtils.writeVarInt(plain, rawLen);
            plain.writeBytes(in, rawLen);
            out.add(plain);
        } else {
            // 已压缩：解压后重新打帧
            if (dataLength > InternalConfig.MAX_UNCOMPRESSED_SIZE) {
                throw new DecoderException("Uncompressed data length " + dataLength
                        + " exceeds maximum " + InternalConfig.MAX_UNCOMPRESSED_SIZE);
            }

            int compressedLen = frameEnd - in.readerIndex();
            if (compressedLen < 0) {
                throw new DecoderException("Negative compressed length: " + compressedLen);
            }
            byte[] compressedBytes = new byte[compressedLen];
            in.readBytes(compressedBytes);

            inflater.setInput(compressedBytes);
            byte[] decompressed = new byte[dataLength];
            try {
                int actual = inflater.inflate(decompressed);
                if (actual != dataLength) {
                    throw new DecoderException("Decompressed size mismatch: expected "
                            + dataLength + ", got " + actual);
                }
            } catch (DataFormatException e) {
                throw new DecoderException("Failed to decompress packet", e);
            } finally {
                inflater.reset();
            }

            // 将解压数据包装为普通帧（length + data）传给下游 PacketDecoder
            ByteBuf framed = ctx.alloc().buffer(MinecraftCodecUtils.varIntSize(dataLength) + dataLength);
            MinecraftCodecUtils.writeVarInt(framed, dataLength);
            framed.writeBytes(decompressed);
            out.add(framed);
        }

        // 确保 readerIndex 移到帧末尾（防御性写法）
        in.readerIndex(frameEnd);
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

