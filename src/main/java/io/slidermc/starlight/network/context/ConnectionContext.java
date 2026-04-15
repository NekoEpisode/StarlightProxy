package io.slidermc.starlight.network.context;

import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

public class ConnectionContext {
    private HandshakeInformation handshakeInformation;
    private ProtocolState inboundState;
    private ProtocolState outboundState;
    private ProxiedPlayer player;

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
}
