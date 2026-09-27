package io.slidermc.starlight.api.channel;

import io.slidermc.starlight.api.translate.TranslateManager;
import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理侧插件消息通道注册表。
 *
 * <p>Paper 的 {@code CraftPlayer#sendPluginMessage} 在发送前会校验目标通道是否存在于该玩家连接的
 * 通道集合中，而该集合由客户端的 {@code minecraft:register} 填充。原版客户端收到
 * {@code minecraft:register} 并不做任何注册，因此"后端插件 → 代理"方向的插件消息必须由代理
 * 主动把通道声明给后端，否则会被 Paper 静默丢弃。
 *
 * <p>代理下发给下游的通道集合为：本注册表中的通道 ∪ 该连接客户端声明过的通道，
 * 见 {@code ConnectionContext#getClientChannels()}。
 *
 * <p>通道标识直接使用 Adventure 的 {@link Key}：代理的插件消息 API 全线使用该类型，
 * 无需再引入一套并行标识体系。
 */
public class ChannelRegistry {

    /**
     * 保留命名空间。{@code minecraft} 下的通道（{@code brand}、{@code register}、
     * {@code unregister} 等）由代理内部处理，插件不得占用。
     */
    public static final String RESERVED_NAMESPACE = "minecraft";

    private final Set<Key> channels = ConcurrentHashMap.newKeySet();
    private final TranslateManager translateManager;

    public ChannelRegistry(TranslateManager translateManager) {
        this.translateManager = translateManager;
    }

    /**
     * 注册通道。
     *
     * @param channels 待注册的通道
     * @throws IllegalArgumentException 若任一通道位于 {@link #RESERVED_NAMESPACE} 命名空间下
     */
    public void register(Key... channels) {
        checkNamespace(channels);
        Collections.addAll(this.channels, channels);
    }

    /**
     * 注销通道。
     *
     * @param channels 待注销的通道
     */
    public void unregister(Key... channels) {
        for (Key channel : channels) {
            this.channels.remove(channel);
        }
    }

    /**
     * 注销指定命名空间下的全部通道，用于插件卸载时批量清理。
     *
     * @param namespace 命名空间，通常为插件 ID
     */
    public void unregisterAll(String namespace) {
        channels.removeIf(channel -> channel.namespace().equals(namespace));
    }

    /**
     * @return 当前已注册通道的不可变快照
     */
    public Set<Key> getChannels() {
        return Set.copyOf(channels);
    }

    public boolean isRegistered(Key channel) {
        return channels.contains(channel);
    }

    private void checkNamespace(Key... channels) {
        for (Key channel : channels) {
            if (RESERVED_NAMESPACE.equals(channel.namespace())) {
                throw new IllegalArgumentException(translateManager.translate(
                        "starlight.logging.error.channel.reserved_namespace", channel.asString()));
            }
        }
    }
}
