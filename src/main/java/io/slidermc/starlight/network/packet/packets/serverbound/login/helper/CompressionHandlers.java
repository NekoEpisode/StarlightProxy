package io.slidermc.starlight.network.packet.packets.serverbound.login.helper;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.channel.CompressionCodecFactory;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.codec.CompressionDecoder;
import io.slidermc.starlight.network.codec.CompressionEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在 {@code Set Compression} 之后安装上游连接的压缩 handler。
 *
 * <p>默认安装 Starlight 自身的 zlib 实现。若插件通过
 * {@link StarlightProxy#setCompressionCodecFactory} 提供了工厂，则改用它的实现——这是插件让连接
 * 改用协商算法（例如 zstd）的介入点。
 *
 * <p>工厂返回 {@code null}、抛异常或返回不完整的 handler 时一律退回默认实现：压缩协商失败不该让
 * 连接断开，慢一点也远好过登不进去。
 */
public final class CompressionHandlers {

    private static final Logger log = LoggerFactory.getLogger(CompressionHandlers.class);

    private CompressionHandlers() {
    }

    /**
     * 安装压缩 handler；已安装时为空操作。
     *
     * @param ctx       上游连接的上下文
     * @param proxy     代理实例，用于取插件提供的工厂
     * @param threshold 即将生效的压缩阈值
     */
    public static void install(ChannelHandlerContext ctx, StarlightProxy proxy, int threshold) {
        boolean alreadyInstalled = ctx.pipeline().get(InternalConfig.HANDLER_DECOMPRESS) != null
                || ctx.pipeline().get(InternalConfig.HANDLER_COMPRESS) != null;
        if (alreadyInstalled) {
            return;
        }

        CompressionCodecFactory.Handlers handlers = null;
        CompressionCodecFactory factory = proxy.getCompressionCodecFactory();
        if (factory != null) {
            try {
                handlers = factory.create(ctx.channel(), threshold);
            } catch (Throwable t) {
                log.warn(proxy.getTranslateManager().translate(
                                "starlight.logging.warn.compression_factory_failed"),
                        ctx.channel().remoteAddress(), t);
            }
        }

        if (handlers != null && handlers.encoder() != null && handlers.decoder() != null) {
            ctx.pipeline().addBefore(InternalConfig.HANDLER_DECODER, InternalConfig.HANDLER_DECOMPRESS,
                    handlers.decoder());
            ctx.pipeline().addBefore(InternalConfig.HANDLER_ENCODER, InternalConfig.HANDLER_COMPRESS,
                    handlers.encoder());
            return;
        }

        ctx.pipeline().addBefore(InternalConfig.HANDLER_DECODER, InternalConfig.HANDLER_DECOMPRESS,
                new CompressionDecoder());
        ctx.pipeline().addBefore(InternalConfig.HANDLER_ENCODER, InternalConfig.HANDLER_COMPRESS,
                new CompressionEncoder(threshold));
    }
}
