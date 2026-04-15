package io.slidermc.starlight.api.server;

import java.net.InetSocketAddress;

public class ProxiedServer {
    private final InetSocketAddress address;
    private final String name;

    public ProxiedServer(InetSocketAddress address, String name) {
        this.address = address;
        this.name = name;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }
}
