package io.slidermc.starlight.api.player;

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
}
