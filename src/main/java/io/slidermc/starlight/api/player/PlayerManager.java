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

    /**
     * 尝试把玩家加入索引；同名或同 UUID 已在时拒绝。
     *
     * <p>检查与插入在同一个 {@code synchronized} 块内完成。<b>不可拆成"先查再插"</b>：两条同名连接
     * 并发登录时，分开的检查会让两者都通过，后到者覆盖先到者，而先到者此时可能已经建好了下游连接。
     *
     * <p>UUID 与用户名都要查，它们拦截的是不同情况：UUID 相同是同一账号重复连接，
     * 用户名相同而 UUID 不同是盗用他人名字。
     *
     * @param player 待加入的玩家
     * @return 加入成功返回 {@code true}；UUID 或用户名已被占用时返回 {@code false}，索引保持不变
     */
    public synchronized boolean tryAddPlayer(ProxiedPlayer player) {
        UUID uuid = player.getGameProfile().uuid();
        String username = player.getGameProfile().username();

        if (uuidToPlayer.containsKey(uuid) || nameToPlayer.containsKey(username)) {
            return false;
        }

        uuidToPlayer.put(uuid, player);
        nameToPlayer.put(username, player);
        return true;
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
