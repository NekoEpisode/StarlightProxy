package io.slidermc.starlight.api.player;

import io.netty.channel.Channel;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;

import java.util.Objects;

public class ProxiedPlayer {
    private final GameProfile gameProfile;
    private final Channel channel;

    public ProxiedPlayer(GameProfile gameProfile, Channel channel) {
        this.gameProfile = gameProfile;
        this.channel = channel;
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public Channel getChannel() {
        return channel;
    }

    public ConnectionContext getConnectionContext() {
        return channel.attr(AttributeKeys.CONNECTION_CONTEXT).get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProxiedPlayer player = (ProxiedPlayer) o;
        return Objects.equals(gameProfile, player.gameProfile) && Objects.equals(channel, player.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameProfile, channel);
    }

    @Override
    public String toString() {
        return "ProxiedPlayer{" +
                "gameProfile=" + gameProfile +
                ", channel=" + channel +
                '}';
    }
}
