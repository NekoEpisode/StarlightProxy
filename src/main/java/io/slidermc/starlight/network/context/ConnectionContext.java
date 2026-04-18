package io.slidermc.starlight.network.context;

import io.netty.channel.Channel;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.data.clientinformation.ClientInformation;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ConnectionContext {
    private HandshakeInformation handshakeInformation;
    private ProtocolState inboundState;
    private ProtocolState outboundState;
    private ProxiedPlayer player;
    /** The downstream server channel paired with this player connection. Set externally when the player is connected to a backend server. */
    private Channel downstreamChannel;
    /** Set by ModernServerSwitcher before sending StartConfiguration; completed by ServerboundConfigurationAckPacket.Listener. */
    private volatile CompletableFuture<Void> pendingReconfiguration;
    private ClientInformation clientInformation;

    public ConnectionContext() {
        this.inboundState = ProtocolState.HANDSHAKE;
        this.outboundState = ProtocolState.HANDSHAKE;
        this.handshakeInformation = new HandshakeInformation();
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
}
