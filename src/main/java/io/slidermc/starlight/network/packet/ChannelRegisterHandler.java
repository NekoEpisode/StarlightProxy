package io.slidermc.starlight.network.packet;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.channel.ChannelPayload;
import io.slidermc.starlight.api.event.events.internal.PlayerChannelRegisteredEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 处理客户端声明的插件消息通道（{@code minecraft:register} / {@code minecraft:unregister}）。
 *
 * <p>必须同步处理：通道集合要在下游的 {@code finish_configuration} 之前备齐，否则代理下发通道声明
 * 时会漏掉客户端侧的部分。因此这里不走异步的事件总线。
 *
 * <p>记录之后是否把原始包转发给下游，取决于下游连接是否已经就绪：
 * <ul>
 *   <li>已就绪（PLAY 阶段）：放行，由通用的插件消息流程转发。客户端在游戏中途才声明的通道
 *       必须立刻同步给下游，否则下游会认为该通道未知而丢弃其上的消息。</li>
 *   <li>未就绪（配置阶段或下游尚未建立）：吞掉该包。此时无法写入下游，而代理在
 *       {@code finish_configuration} 时会把自己的通道与该连接已知的客户端通道聚合后统一下发。</li>
 * </ul>
 */
public final class ChannelRegisterHandler {

    private static final Logger log = LoggerFactory.getLogger(ChannelRegisterHandler.class);

    private static final Key REGISTER = ChannelPayload.REGISTER_CHANNEL;
    private static final Key UNREGISTER = ChannelPayload.UNREGISTER_CHANNEL;

    /** 单连接允许的客户端通道上限，防止恶意客户端用超长 register 撑爆内存。 */
    private static final int MAX_CLIENT_CHANNELS = 128;

    private ChannelRegisterHandler() {
    }

    /**
     * 记录客户端声明的通道，并判断该包是否还需继续走通用的插件消息流程。
     *
     * @param key   插件消息通道
     * @param data  载荷
     * @param ctx   收到该包的连接上下文
     * @param proxy 代理实例
     * @return {@code true} 表示该包已被消费、不应再转发；{@code false} 表示应继续转发给下游
     */
    public static boolean handle(Key key, byte[] data, ChannelHandlerContext ctx, StarlightProxy proxy) {
        boolean register = REGISTER.equals(key);
        if (!register && !UNREGISTER.equals(key)) {
            return false;
        }

        ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
        if (context == null) {
            return true;
        }

        Set<Key> added = new LinkedHashSet<>();
        int refused = 0;

        for (Key channel : ChannelPayload.parse(data)) {
            if (!register) {
                context.removeClientChannel(channel);
            } else if (context.getClientChannelCount() >= MAX_CLIENT_CHANNELS) {
                refused++;
            } else if (context.addClientChannel(channel)) {
                added.add(channel);
            }
        }

        if (refused > 0) {
            log.warn(proxy.getTranslateManager().translate(
                    "starlight.logging.warn.channel.client_limit_exceeded"), MAX_CLIENT_CHANNELS, refused);
        }

        fireEvent(context, proxy, added);

        // 下游尚未建立时无法转发，此时由 finish_configuration 的聚合下发负责
        return context.getDownstreamChannel() == null;
    }

    /**
     * 玩家已进入 PLAY 阶段时，把本次新增声明的通道广播给插件。
     */
    private static void fireEvent(ConnectionContext context, StarlightProxy proxy, Set<Key> added) {
        if (added.isEmpty()) {
            return;
        }

        ProxiedPlayer player = context.getPlayer();
        if (player == null) {
            return;
        }

        proxy.getEventManager().fireAsync(new PlayerChannelRegisteredEvent(player, Set.copyOf(added)));
    }
}

