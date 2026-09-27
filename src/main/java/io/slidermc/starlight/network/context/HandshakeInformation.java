package io.slidermc.starlight.network.context;

import io.slidermc.starlight.network.protocolenum.NextState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

public class HandshakeInformation {
    private int originalProtocolVersion;
    private ProtocolVersion protocolVersion;
    private ServerHost serverHost;
    private short serverPort;
    private NextState nextState;

    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public ServerHost getServerHost() {
        return serverHost;
    }

    public void setServerHost(ServerHost serverHost) {
        this.serverHost = serverHost;
    }

    public short getServerPort() {
        return serverPort;
    }

    public void setServerPort(short serverPort) {
        this.serverPort = serverPort;
    }

    public NextState getNextState() {
        return nextState;
    }

    public void setNextState(NextState nextState) {
        this.nextState = nextState;
    }

    public int getOriginalProtocolVersion() {
        return originalProtocolVersion;
    }

    public void setOriginalProtocolVersion(int originalProtocolVersion) {
        this.originalProtocolVersion = originalProtocolVersion;
    }
}
