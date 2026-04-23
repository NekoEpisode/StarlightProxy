package io.slidermc.starlight.api.server;

import java.net.InetSocketAddress;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProxiedServer that = (ProxiedServer) o;
        return Objects.equals(address, that.address) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, name);
    }

    @Override
    public String toString() {
        return "ProxiedServer{" +
                "address=" + address +
                ", name='" + name + '\'' +
                '}';
    }
}
