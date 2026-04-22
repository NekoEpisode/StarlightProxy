package io.slidermc.starlight.api.command.source;

import io.slidermc.starlight.api.player.ProxiedPlayer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.Set;

public interface IStarlightCommandSource {
    void sendMessage(Component message);
    Optional<ProxiedPlayer> asProxiedPlayer();

    /**
     * 向上下文中存入一个插件自定义值。
     *
     * @param key   类型安全的 key，建议使用 namespaced 命名（如 {@code "myplugin:real_ip"}）
     * @param value 值
     */
    <T> void setContext(ContextKey<T> key, T value);

    /**
     * 读取上下文中的插件自定义值。
     *
     * @param key 与写入时相同的 key 实例
     * @return 值，不存在时为 {@link Optional#empty()}
     */
    <T> Optional<T> getContext(ContextKey<T> key);

    /**
     * 返回当前已存入的所有 key。
     */
    Set<ContextKey<?>> contextKeys();

    /**
     * 检查此CommandSource时候持有某个权限
     * @return 结果
     */
    boolean hasPermission(String permission);
}
