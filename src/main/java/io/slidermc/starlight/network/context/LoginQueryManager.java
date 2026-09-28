package io.slidermc.starlight.network.context;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundPluginRequestPacket;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 登录阶段的插件消息查询（Login Plugin Query）。
 *
 * <p>这是"一问一答"的事务模型，而非事件广播：代理在登录阶段向客户端发一条
 * {@code minecraft:custom_query}，客户端用 {@code minecraft:custom_query_answer} 回复，
 * 回复按消息 ID 路由回发起者。它和 PLAY 阶段的插件消息（{@link ConnectionContext} 里的
 * 通道集合与对应的广播事件）是两套协议、两个阶段，因此不共用事件机制。
 *
 * <p><b>登录流程必须等所有查询结束</b>才能继续。调用方在推进登录之前先等
 * {@link #allSettled()}，否则查询会与加密请求等后续包交错。
 *
 * <p>客户端不认识的通道会以"无数据"应答——这正好用来判断"客户端不具备该能力"，
 * 因此失败的查询同样会完成，只是结果为 {@code null}。它<b>不会</b>让登录流程卡住。
 *
 * <p>不设超时：原版客户端对任何 {@code custom_query} 都会在一个往返内给出应答，
 * 不存在"既不理解也不回复"的客户端。连接断开时由 {@link #cleanup()} 兜底完成所有待决查询，
 * 避免登录流程永久挂起。
 */
public final class LoginQueryManager {

    private static final Logger log = LoggerFactory.getLogger(LoginQueryManager.class);

    /** 单条查询数据的长度上限，防御畸形包导致的异常分配。 */
    private static final int MAX_QUERY_DATA_LENGTH = 1024 * 1024;

    private final Channel channel;
    private final ConnectionContext context;
    private final ChannelRegistryView channelRegistry;

    /** 消息 ID → 待回复的查询。ID 在单条连接内单调递增，天然不冲突。 */
    private final Map<Integer, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();

    private final AtomicInteger sequence = new AtomicInteger();

    /**
     * @param channel          上游连接，用于写出查询
     * @param context          连接上下文，用于取协议版本
     * @param channelRegistry  通道注册表，用于校验插件已声明该通道
     */
    LoginQueryManager(Channel channel, ConnectionContext context, ChannelRegistryView channelRegistry) {
        this.channel = channel;
        this.context = context;
        this.channelRegistry = channelRegistry;
    }

    /**
     * 向客户端发起一次查询。
     *
     * <p>结果为客户端回复的内容；客户端不理解该通道时以 {@code null} 完成。
     * 连接已断开或协议版本不支持时同样立即以 {@code null} 完成，调用方无需特判。
     *
     * @param channelKey 查询使用的通道，必须已在代理侧声明
     * @param data       查询内容
     * @return 客户端应答的 future；{@code null} 表示客户端不支持该查询
     */
    public CompletableFuture<byte[]> query(Key channelKey, byte[] data) {
        if (channelKey == null) {
            return failed("查询通道不能为 null");
        }
        if (data == null) {
            return failed("查询内容不能为 null");
        }
        if (data.length > MAX_QUERY_DATA_LENGTH) {
            return failed("查询内容过长: " + data.length + " 字节");
        }
        if (!channel.isActive()) {
            log.debug("连接已关闭，跳过通道 {} 的查询", channelKey.asString());
            return unsupported();
        }
        if (channelRegistry != null && !channelRegistry.isRegistered(channelKey)) {
            log.warn("插件在未声明的通道 [{}] 上发起登录查询，已忽略", channelKey.asString());
            return unsupported();
        }

        int messageId = sequence.incrementAndGet();
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        pending.put(messageId, result);

        // 先登记再写出：应答可能在下一次事件循环迭代就到达
        ByteBuf payload = Unpooled.wrappedBuffer(data);
        channel.writeAndFlush(new ClientboundPluginRequestPacket(messageId, channelKey, payload))
                .addListener(future -> {
                    if (!future.isSuccess()) {
                        CompletableFuture<byte[]> removed = pending.remove(messageId);
                        if (removed != null) {
                            removed.complete(null);
                        }
                    }
                });

        log.debug("已发起登录查询: 通道={} id={} 长度={}", channelKey.asString(), messageId, data.length);
        return result;
    }

    /**
     * 处理客户端对查询的应答。
     *
     * <p>由 {@code ServerboundPluginResponsePacket} 的监听器调用。未知的消息 ID 会被忽略：
     * 它可能来自与本次登录无关的查询，或应答被重复投递。
     *
     * @param messageId 应答针对的消息 ID
     * @param hasData   客户端是否给出了内容；为 {@code false} 表示它不理解该通道
     * @param data      应答内容，{@code hasData} 为 {@code false} 时无意义
     */
    public void complete(int messageId, boolean hasData, byte[] data) {
        CompletableFuture<byte[]> result = pending.remove(messageId);
        if (result == null) {
            log.debug("收到未知消息 ID 的登录应答，已忽略: id={}", messageId);
            return;
        }

        // 客户端不理解该通道时以 null 完成，调用方据此回退到默认行为
        result.complete(hasData ? data : null);
        log.debug("登录查询已应答: id={} 有数据={}", messageId, hasData);
    }

    /**
     * 等待所有查询结束。
     *
     * <p>用于在推进登录流程之前确认没有任何查询仍在等待应答。
     *
     * @return 全部查询结束后的信号
     */
    public CompletableFuture<Void> allSettled() {
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<?>[] outstanding = pending.values().toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(outstanding).exceptionally(throwable -> null);
    }

    /**
     * 连接结束时清理：所有待决查询以 {@code null} 完成。
     *
     * <p>没有这一步，等待应答的登录流程会永久挂起。
     */
    public void cleanup() {
        for (Map.Entry<Integer, CompletableFuture<byte[]>> entry : pending.entrySet()) {
            CompletableFuture<byte[]> result = pending.remove(entry.getKey());
            if (result != null) {
                result.complete(null);
            }
        }
    }

    /**
     * 是否已有未结束的查询。
     *
     * @return 存在待决查询时返回 {@code true}
     */
    public boolean hasPendingQueries() {
        return !pending.isEmpty();
    }

    private static CompletableFuture<byte[]> unsupported() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<byte[]> failed(String reason) {
        log.warn("登录查询参数无效: {}", reason);
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        result.complete(null);
        return result;
    }

    /**
     * 通道注册表的只读视图。
     *
     * <p>把 {@code ChannelRegistry} 收窄到登录查询真正需要的一个方法，避免这个底层上下文
     * 反向依赖 api 包里的具体实现。
     */
    public interface ChannelRegistryView {

        /**
         * 该通道是否已被插件声明。
         *
         * @param channel 通道
         * @return 已声明时返回 {@code true}
         */
        boolean isRegistered(Key channel);
    }

    /**
     * 便捷方法：注册一个在应答到达时执行的动作。
     *
     * @param channelKey 查询通道
     * @param data       查询内容
     * @param onResponse 应答回调；参数为 {@code null} 表示客户端不理解该通道
     */
    public void query(Key channelKey, byte[] data, Consumer<byte[]> onResponse) {
        query(channelKey, data).thenAccept(onResponse);
    }

    /**
     * 通道属性键，便于从 channel 上取回管理器（例如调试或断连清理）。
     */
    public static final AttributeKey<LoginQueryManager> ATTRIBUTE =
            AttributeKey.newInstance("starlight:login_queries");
}
