package io.slidermc.starlight.api.player;

import io.slidermc.starlight.api.server.ProxiedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家管理器，维护 UUID 和用户名双向索引。
 *
 * <p>所有对双 Map 的复合操作（add/remove）均使用 {@code synchronized} 保护原子性，
 * 避免并发时两个 Map 之间出现不一致的中间状态。
 */
public class PlayerManager {
    private final Map<UUID, ProxiedPlayer> uuidToPlayer = new ConcurrentHashMap<>();
    private final Map<String, ProxiedPlayer> nameToPlayer = new ConcurrentHashMap<>();

    public ProxiedPlayer getPlayer(UUID uuid) {
        return uuidToPlayer.get(uuid);
    }

    public ProxiedPlayer getPlayer(String name) {
        return nameToPlayer.get(name);
    }

    public synchronized void addPlayer(ProxiedPlayer player) {
        uuidToPlayer.put(player.getGameProfile().uuid(), player);
        nameToPlayer.put(player.getGameProfile().username(), player);
    }

    public synchronized ProxiedPlayer removePlayer(UUID uuid) {
        ProxiedPlayer player = uuidToPlayer.remove(uuid);
        if (player != null) {
            nameToPlayer.remove(player.getGameProfile().username());
        }
        return player;
    }

    public synchronized ProxiedPlayer removePlayer(String name) {
        ProxiedPlayer player = nameToPlayer.remove(name);
        if (player != null) {
            uuidToPlayer.remove(player.getGameProfile().uuid());
        }
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
