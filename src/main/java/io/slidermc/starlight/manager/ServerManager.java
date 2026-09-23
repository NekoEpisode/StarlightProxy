package io.slidermc.starlight.manager;

import io.slidermc.starlight.api.server.ProxiedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerManager {
    private final Map<String, ProxiedServer> serverMap = new ConcurrentHashMap<>();
    private final Map<String, String> forceHostMap = new ConcurrentHashMap<>();

    public ServerManager(ProxiedServer defaultServer) {
        serverMap.put(defaultServer.getName(), defaultServer);
    }

    public void addServer(ProxiedServer server) {
        serverMap.put(server.getName(), server);
    }

    public void addForceHost(String addr, String serverName) {
        if (!serverMap.containsKey(serverName)) {
            throw new IllegalArgumentException("Forced host server '" + serverName + "' not exists!");
        }
        forceHostMap.put(addr, serverName);
    }

    public ProxiedServer removeServer(ProxiedServer server) {
        forceHostMap.entrySet().removeIf(e -> e.getValue().equals(server.getName()));
        return serverMap.remove(server.getName());
    }

    public ProxiedServer removeServer(String name) {
        forceHostMap.entrySet().removeIf(e -> e.getValue().equals(name));
        return serverMap.remove(name);
    }

    public ProxiedServer getServer(String name) {
        return serverMap.get(name);
    }

    public String getForceHostName(String addr) {
        return forceHostMap.get(addr);
    }

    public ProxiedServer getForceHostServer(String addr) {
        String serverName = forceHostMap.get(addr);
        if (serverName == null) return null;
        return serverMap.get(serverName);
    }

    public List<ProxiedServer> getServers() {
        return new ArrayList<>(serverMap.values());
    }

    public ProxiedServer getForceHostDefaultServer() {
        String name = forceHostMap.get("default");
        if (name == null) return null;
        return serverMap.get(name);
    }
}
