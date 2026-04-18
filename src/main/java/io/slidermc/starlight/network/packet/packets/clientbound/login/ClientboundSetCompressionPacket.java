package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.CompressionDecoder;
import io.slidermc.starlight.network.codec.CompressionEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientboundSetCompressionPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundSetCompressionPacket.class);
    private int threshold;

    public ClientboundSetCompressionPacket() {}

    public ClientboundSetCompressionPacket(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, threshold);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.threshold = MinecraftCodecUtils.readVarInt(byteBuf);
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public static class Listener implements IPacketListener<ClientboundSetCompressionPacket> {
        @Override
        public void handle(ClientboundSetCompressionPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            if (packet.threshold >= 0) {
                ctx.pipeline().addBefore("decoder", "decompress", new CompressionDecoder());
                ctx.pipeline().addBefore("encoder", "compress", new CompressionEncoder(packet.threshold));
                log.debug("下游已启动Compression: {}", packet.threshold);
            }
        }
    }
}
