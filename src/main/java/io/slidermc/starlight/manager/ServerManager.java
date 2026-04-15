package io.slidermc.starlight.manager;

import io.slidermc.starlight.api.server.ProxiedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerManager {
    private final String defaultServer;

    private final Map<String, ProxiedServer> serverMap = new ConcurrentHashMap<>();

    public ServerManager(ProxiedServer defaultServer) {
        this.defaultServer = defaultServer.getName();
        serverMap.put(defaultServer.getName(), defaultServer);
    }

    public void addServer(ProxiedServer server) {
        serverMap.put(server.getName(), server);
    }

    public ProxiedServer removeServer(ProxiedServer server) {
        return serverMap.remove(server.getName());
    }

    public ProxiedServer removeServer(String name) {
        return serverMap.remove(name);
    }

    public ProxiedServer getServer(String name) {
        return serverMap.get(name);
    }

    public List<ProxiedServer> getServers() {
        return new ArrayList<>(serverMap.values());
    }

    public ProxiedServer getDefaultServer() {
        return serverMap.get(defaultServer);
    }
}
