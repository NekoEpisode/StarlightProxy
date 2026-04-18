package io.slidermc.starlight.api.player;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.ContextKey;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundSystemChatPacket;
import io.slidermc.starlight.switcher.ModernServerSwitcher;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ProxiedPlayer implements IStarlightCommandSource {
    private static final int RECONFIGURATION_MIN_VERSION = 764;

    private final GameProfile gameProfile;
    private final Channel channel;
    private final StarlightProxy proxy;

    private final Map<ContextKey<?>, Object> contextMap = new ConcurrentHashMap<>();

    private volatile ProxiedServer currentServer;

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

    public Optional<ProxiedServer> getCurrentServer() {
        return Optional.ofNullable(currentServer);
    }

    public void setCurrentServer(ProxiedServer server) {
        this.currentServer = server;
    }

    @Override
    public void sendMessage(Component component) {
        channel.writeAndFlush(new ClientboundSystemChatPacket(component, false));
    }

    public void sendActionbar(Component component) {
        channel.writeAndFlush(new ClientboundSystemChatPacket(component, true));
    }

    @Override
    public Optional<ProxiedPlayer> asProxiedPlayer() {
        return Optional.of(this);
    }

    @Override
    public <T> void setContext(ContextKey<T> key, T value) {
        contextMap.put(key, value);
    }

    @Override
    public <T> Optional<T> getContext(ContextKey<T> key) {
        Object value = contextMap.get(key);
        if (value == null) return Optional.empty();
        return Optional.of(key.type().cast(value));
    }

    @Override
    public Set<ContextKey<?>> contextKeys() {
        return Collections.unmodifiableSet(contextMap.keySet());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProxiedPlayer player = (ProxiedPlayer) o;
        return Objects.equals(gameProfile, player.gameProfile) && Objects.equals(channel, player.channel) && Objects.equals(proxy, player.proxy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameProfile, channel, proxy);
    }

    @Override
    public String toString() {
        return "ProxiedPlayer{" +
                "gameProfile=" + gameProfile +
                ", channel=" + channel +
                ", proxy=" + proxy +
                '}';
    }
}
