package io.slidermc.starlight.api.player;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.ContextKey;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundPluginMessageConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundPluginMessagePlayPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundSystemChatPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.switcher.ModernServerSwitcher;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ProxiedPlayer implements IStarlightCommandSource {
    private static final int RECONFIGURATION_MIN_VERSION = 764;
    private static final Logger log = LoggerFactory.getLogger(ProxiedPlayer.class);

    private final GameProfile gameProfile;
    private final Channel channel;
    private final StarlightProxy proxy;

    private final ConcurrentLinkedDeque<InQueueMessage> pendingMessageQueue = new ConcurrentLinkedDeque<>();
    private volatile boolean canSendMessages = false;

    private final Map<ContextKey<?>, Object> contextMap = new ConcurrentHashMap<>();

    private volatile ProxiedServer currentServer;
    private volatile ProxiedServer previousServer;

    private volatile boolean isOnline;

    public ProxiedPlayer(GameProfile gameProfile, Channel channel, StarlightProxy proxy, boolean isOnline) {
        this.gameProfile = gameProfile;
        this.channel = channel;
        this.proxy = proxy;
        this.isOnline = isOnline;
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
        log.debug("当前服务器设置到: {}", server);
    }

    /**
     * 发送指定消息给玩家
     * 如果玩家尚未进入PLAY阶段，则消息积压，直到进入PLAY阶段后在LoginPlay包后发送
     * @param component 要发送的消息
     */
    @Override
    public void sendMessage(Component component) {
        if (channel.eventLoop().inEventLoop()) {
            doSendMessage(component);
        } else {
            channel.eventLoop().execute(() -> doSendMessage(component));
        }
    }

    private void doSendMessage(Component component) {
        if (!canSendMessages) {
            pendingMessageQueue.addLast(new InQueueMessage(component, false));
            return;
        }
        channel.writeAndFlush(new ClientboundSystemChatPacket(component, false));
    }

    /**
     * 发送Actionbar消息，积压机制与{@code sendMessage(Component)}相同
     * @param component 要发送的消息
     */
    public void sendActionbar(Component component) {
        if (channel.eventLoop().inEventLoop()) {
            doSendActionbar(component);
        } else {
            channel.eventLoop().execute(() -> doSendActionbar(component));
        }
    }

    private void doSendActionbar(Component component) {
        if (!canSendMessages) {
            pendingMessageQueue.addLast(new InQueueMessage(component, true));
            return;
        }
        channel.writeAndFlush(new ClientboundSystemChatPacket(component, true));
    }

    public void sendAllPendingMessages() {
        if (!channel.eventLoop().inEventLoop()) {
            channel.eventLoop().execute(this::sendAllPendingMessages);
            return;
        }

        if (getConnectionContext().getOutboundState() != ProtocolState.PLAY) {
            throw new IllegalStateException("Can only send pending messages in PLAY state");
        }

        InQueueMessage msg;
        while ((msg = pendingMessageQueue.pollFirst()) != null) {
            if (msg.actionbar) {
                doSendActionbar(msg.component);
            } else {
                doSendMessage(msg.component);
            }
        }
    }

    public CompletableFuture<Void> sendPacket(IMinecraftPacket packet) {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        channel.writeAndFlush(packet).addListener(future -> {
            if (future.isSuccess()) {
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(future.cause());
            }
        });
        return completableFuture;
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
    public boolean hasPermission(String permission) {
        throw new UnsupportedOperationException("Permission system not implemented yet"); // TODO: 实现权限系统
    }

    public CompletableFuture<Void> sendPluginMessage(Key key, byte[] data) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            if (getConnectionContext().getOutboundState() == ProtocolState.LOGIN) {
                throw new IllegalStateException("Cannot send plugin message during login phase");
            } else if (getConnectionContext().getOutboundState() == ProtocolState.HANDSHAKE) {
                throw new IllegalStateException("Cannot send plugin message during handshake phase");
            } else if (getConnectionContext().getOutboundState() == ProtocolState.CONFIGURATION) {
                channel.writeAndFlush(new ClientboundPluginMessageConfigurationPacket(key, data)).addListener(_ ->
                        future.complete(null));
            } else if (getConnectionContext().getOutboundState() == ProtocolState.PLAY) {
                channel.writeAndFlush(new ClientboundPluginMessagePlayPacket(key, data)).addListener(_ ->
                        future.complete(null));
            } else {
                throw new IllegalStateException("Unknown protocol state: " + getConnectionContext().getOutboundState());
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Optional<ProxiedServer> getPreviousServer() {
        return Optional.ofNullable(previousServer);
    }

    public void setPreviousServer(ProxiedServer previousServer) {
        this.previousServer = previousServer;
        log.debug("上一个服务器设置到: {}", previousServer);
    }

    public boolean isCanSendMessages() {
        return canSendMessages;
    }

    public void setCanSendMessages(boolean canSendMessages) {
        this.canSendMessages = canSendMessages;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProxiedPlayer player = (ProxiedPlayer) o;
        return Objects.equals(gameProfile.uuid(), player.gameProfile.uuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameProfile, channel, proxy, pendingMessageQueue, contextMap, currentServer, previousServer);
    }

    @Override
    public String toString() {
        return "ProxiedPlayer{" +
                "gameProfile=" + gameProfile +
                ", channel=" + channel +
                ", proxy=" + proxy +
                ", pendingMessageQueue=" + pendingMessageQueue +
                ", contextMap=" + contextMap +
                ", currentServer=" + currentServer +
                ", previousServer=" + previousServer +
                '}';
    }

    private record InQueueMessage(Component component, boolean actionbar) {}
}
