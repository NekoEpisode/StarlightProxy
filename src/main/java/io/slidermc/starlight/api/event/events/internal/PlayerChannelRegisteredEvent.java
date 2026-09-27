package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * 客户端声明了新的插件消息通道时触发。
 *
 * <p>数据来源是客户端的 {@code minecraft:register} 载荷，代理会把这些通道连同自己注册的通道
 * 一起下发给下游服务器。玩家尚未进入 PLAY 阶段时不会触发（此时还没有可派发的玩家对象）。
 *
 * @param getPlayer 参数名 getPlayer 是为了自动满足 IPlayerEvent 接口的 getPlayer() 方法要求
 * @param channels  本次新增声明的通道
 */
public record PlayerChannelRegisteredEvent(ProxiedPlayer getPlayer, Set<Key> channels) implements IPlayerEvent {
}
