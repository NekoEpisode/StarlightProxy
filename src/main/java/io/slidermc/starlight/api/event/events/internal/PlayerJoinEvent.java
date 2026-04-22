package io.slidermc.starlight.api.event.events.internal;

import io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;

/**
 * 玩家加入事件
 * @param getPlayer 参数名 getPlayer 是为了自动满足 IPlayerEvent 接口的 getPlayer() 方法要求
 *                  没人比我更懂怎么玩hack👍
 */
public record PlayerJoinEvent(ProxiedPlayer getPlayer) implements IPlayerEvent {
}
