package io.slidermc.starlight.api.player;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.switcher.ModernServerSwitcher;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ProxiedPlayer {
    private static final int RECONFIGURATION_MIN_VERSION = 764;

    private final GameProfile gameProfile;
    private final Channel channel;
    private final StarlightProxy proxy;

    public ProxiedPlayer(GameProfile gameProfile, Channel channel, StarlightProxy proxy) {
        this.gameProfile = gameProfile;
        this.channel = channel;
        this.proxy = proxy;
    }

    public CompletableFuture<Void> connect(ProxiedServer target) {
        int version = getConnectionContext().getHandshakeInformation().getOriginalProtocolVersion();
        if (version >= RECONFIGURATION_MIN_VERSION) {
            return ModernServerSwitcher.switchServer(this, target, proxy);
        }
        // TODO: LegacyServerSwitcher (pre-1.20.2 Respawn Hack)
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Server switching not supported for protocol " + version));
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public Channel getChannel() {
        return channel;
    }

    public StarlightProxy getProxy() {
        return proxy;
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
