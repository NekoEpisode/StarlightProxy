package io.slidermc.starlight.api.player;

import io.slidermc.starlight.api.server.ProxiedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {
    private final Map<UUID, ProxiedPlayer> uuidToPlayer = new ConcurrentHashMap<>();
    private final Map<String, ProxiedPlayer> nameToPlayer = new ConcurrentHashMap<>();

    public ProxiedPlayer getPlayer(UUID uuid) {
        return uuidToPlayer.get(uuid);
    }

    public ProxiedPlayer getPlayer(String name) {
        return nameToPlayer.get(name);
    }

    public void addPlayer(ProxiedPlayer player) {
        uuidToPlayer.put(player.getGameProfile().uuid(), player);
        nameToPlayer.put(player.getGameProfile().username(), player);
    }

    public ProxiedPlayer removePlayer(UUID uuid) {
        ProxiedPlayer player = uuidToPlayer.remove(uuid);
        nameToPlayer.remove(player.getGameProfile().username());
        return player;
    }

    public ProxiedPlayer removePlayer(String name) {
        ProxiedPlayer player = nameToPlayer.remove(name);
        uuidToPlayer.remove(player.getGameProfile().uuid());
        return player;
    }

    public List<ProxiedPlayer> getPlayers() {
        return new ArrayList<>(uuidToPlayer.values());
    }

    /**
     * 返回当前连接到指定服务器的玩家列表。
     *
     * @param server 目标服务器
     * @return 在该服务器上的玩家，顺序不保证
     */
    public List<ProxiedPlayer> getPlayers(ProxiedServer server) {
        return uuidToPlayer.values().stream()
                .filter(p -> p.getCurrentServer().map(s -> s.getName().equals(server.getName())).orElse(false))
                .toList();
    }
}
