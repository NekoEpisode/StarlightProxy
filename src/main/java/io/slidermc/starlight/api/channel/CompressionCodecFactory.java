package io.slidermc.starlight.api.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;

/**
 * 供插件替换上游连接的压缩编解码器。
 *
 * <p>Starlight 在发送 {@code Set Compression} 之后安装自己的 zlib 压缩 handler。插件可能需要让这条
 * 连接改用协商出来的算法——例如与客户端 mod 约定 zstd。这个接口就是那个介入点：插件注册一个工厂，
 * Starlight 在启用压缩时改用它返回的 handler。
 *
 * <p>约束：
 * <ul>
 *   <li>返回的 handler 会以同名的位置装入 pipeline，因此必须自己完成长度前缀与内容长度的读写，
 *       不能假设外层还有别的 handler 代劳。</li>
 *   <li>一对 handler 属于一条连接。{@link CompressionCodecFactory.Handlers} 里的实例会被安装到
 *       给定 channel 上，工厂不应复用同一批实例。</li>
 *   <li>工厂返回 {@code null} 或抛异常时，Starlight 退回自身的 zlib handler，连接不会被中断。</li>
 * </ul>
 */
public interface CompressionCodecFactory {

    /**
     * 为一对方向创建压缩 handler。
     *
     * @param channel   该连接
     * @param threshold 即将生效的压缩阈值
     * @return 一对 handler；返回 {@code null} 表示不接管，由 Starlight 使用自身实现
     */
    Handlers create(Channel channel, int threshold);

    /**
     * 一条连接上的一对压缩 handler。
     *
     * @param encoder 客户端出站方向（代理 → 客户端）
     * @param decoder 客户端入站方向（客户端 → 代理）
     */
    record Handlers(ChannelHandler encoder, ChannelHandler decoder) {}
}
