package io.slidermc.starlight.network.context;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.data.clientinformation.ClientInformation;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 上游（玩家客户端）连接的上下文信息。
 *
 * <p>此类中的字段均为 {@code volatile}，因为可能从 Netty 上游 EventLoop、下游 EventLoop、
 * 事件线程池等多个线程访问。对于引用可变对象（如 {@code byte[]}）的字段，getter/setter
 * 使用防御性拷贝以确保发布后不会被外部修改。
 */
public class ConnectionContext {
    private volatile HandshakeInformation handshakeInformation;
    private volatile ProtocolState inboundState;
    private volatile ProtocolState outboundState;
    private volatile ProxiedPlayer player;
    /** The downstream server channel paired with this player connection. Set externally when the player is connected to a backend server. */
    private volatile Channel downstreamChannel;
    /** Set by ModernServerSwitcher before sending StartConfiguration; completed by ServerboundConfigurationAckPacket.Listener. */
    private volatile CompletableFuture<Void> pendingReconfiguration;
    private volatile ClientInformation clientInformation;
    private volatile byte[] verifyToken;
    /** 正版验证流程中暂存的用户名，EncryptionResponse.Listener 使用后可清除 */
    private volatile String pendingUsername;

    private final StarlightProxy proxy;

    public ConnectionContext(StarlightProxy proxy) {
        this.inboundState = ProtocolState.HANDSHAKE;
        this.outboundState = ProtocolState.HANDSHAKE;
        this.handshakeInformation = new HandshakeInformation();
        this.proxy = proxy;
    }

    public ProtocolState getInboundState() {
        return inboundState;
    }

    public void setInboundState(ProtocolState inboundState) {
        this.inboundState = inboundState;
    }

    public ProtocolState getOutboundState() {
        return outboundState;
    }

    public void setOutboundState(ProtocolState outboundState) {
        this.outboundState = outboundState;

        if (outboundState != ProtocolState.PLAY) {
            ProxiedPlayer p = this.player;
            if (p != null) {
                p.setCanSendMessages(false);
            }
        }
    }

    public HandshakeInformation getHandshakeInformation() {
        return handshakeInformation;
    }

    public void setHandshakeInformation(HandshakeInformation handshakeInformation) {
        this.handshakeInformation = handshakeInformation;
    }

    public ProxiedPlayer getPlayer() {
        return player;
    }

    public void setPlayer(ProxiedPlayer player) {
        this.player = player;
    }

    public Channel getDownstreamChannel() {
        return downstreamChannel;
    }

    public void setDownstreamChannel(Channel downstreamChannel) {
        this.downstreamChannel = downstreamChannel;
    }

    public CompletableFuture<Void> getPendingReconfiguration() {
        return pendingReconfiguration;
    }

    public void setPendingReconfiguration(CompletableFuture<Void> pendingReconfiguration) {
        this.pendingReconfiguration = pendingReconfiguration;
    }

    public Optional<ClientInformation> getClientInformation() {
        return Optional.ofNullable(clientInformation);
    }

    public void setClientInformation(ClientInformation clientInformation) {
        this.clientInformation = clientInformation;
    }

    public byte[] getVerifyToken() {
        byte[] token = this.verifyToken;
        return token != null ? token.clone() : null;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken != null ? verifyToken.clone() : null;
    }

    public String getPendingUsername() {
        return pendingUsername;
    }

    public void setPendingUsername(String pendingUsername) {
        this.pendingUsername = pendingUsername;
    }

    public StarlightProxy getProxy() {
        return proxy;
    }

    public String getTranslation(String key) {
        String locale = (getClientInformation().isPresent() ? getClientInformation().get().getLocale() : proxy.getTranslateManager().getActiveLocale());
        return proxy.getTranslateManager().translate(locale, key);
    }

    public String getLocale() {
        return (getClientInformation().isPresent() ? getClientInformation().get().getLocale() : proxy.getTranslateManager().getActiveLocale());
    }

    public CompletableFuture<Void> toDownstream(IMinecraftPacket packet) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Channel downstream = this.downstreamChannel;
        if (downstream != null) {
            downstream.writeAndFlush(packet).addListener(ctx -> {
                if (ctx.isSuccess()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(ctx.cause());
                }
            });
        } else {
            future.completeExceptionally(new IllegalStateException("Downstream channel is null"));
        }
        return future;
    }
}
