package io.slidermc.starlight.permission;

import io.slidermc.starlight.api.permission.PermissionService;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimplePermissionManager implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(SimplePermissionManager.class);

    private final Path configPath;
    private final ConcurrentMap<UUID, Set<String>> permissions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ioExecutor;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private ScheduledFuture<?> saveFuture;

    public SimplePermissionManager(Path configPath, ScheduledExecutorService ioExecutor) {
        this.configPath = configPath;
        this.ioExecutor = ioExecutor;
    }

    /**
     * 启动定期刷盘任务（每 1 秒检查一次 dirty 标记）。
     * 应在 {@link #load()} 之后调用。
     */
    public void start() {
        saveFuture = ioExecutor.scheduleWithFixedDelay(this::doFlush, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 停止定期刷盘任务，并执行最后一次 flush。
     */
    public void stop() {
        if (saveFuture != null) {
            saveFuture.cancel(false);
        }
        doFlush();
    }

    private void doFlush() {
        try {
            if (dirty.compareAndSet(true, false)) {
                try {
                    save();
                } catch (IOException e) {
                    log.error("Failed to save permissions", e);
                    dirty.set(true);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error in permission save task", e);
        }
    }

    private void markDirty() {
        dirty.set(true);
    }

    /**
     * 立即将当前内存中的权限写入磁盘（同步）。
     */
    private void save() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, List<String>> permMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : permissions.entrySet()) {
            List<String> list = new ArrayList<>(entry.getValue());
            if (!list.isEmpty()) {
                permMap.put(entry.getKey().toString(), list);
            }
        }
        root.put("permissions", permMap);

        Path parent = configPath.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (Writer writer = Files.newBufferedWriter(configPath)) {
            new Yaml().dump(root, writer);
        }
    }

    public void load() throws IOException {
        if (!Files.exists(configPath)) {
            log.info("Permissions file not found; using empty permissions");
            permissions.clear();
            return;
        }

        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(configPath)) {
            root = new Yaml().load(in);
        }

        permissions.clear();

        if (root == null) return;

        Object raw = root.get("permissions");
        if (!(raw instanceof Map<?, ?> permMap)) return;

        for (Map.Entry<?, ?> entry : permMap.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey().toString());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in permissions file: {}", entry.getKey());
                continue;
            }

            Set<String> perms = ConcurrentHashMap.newKeySet();
            if (entry.getValue() instanceof Iterable<?> list) {
                for (Object p : list) {
                    if (p != null) perms.add(p.toString());
                }
            }
            permissions.put(uuid, perms);
        }

        log.info("Loaded {} permission entries", permissions.size());
    }

    @Override
    public boolean hasPermission(UUID playerUuid, String permission) {
        Set<String> perms = permissions.get(playerUuid);
        if (perms == null || perms.isEmpty()) return false;
        return matchWildcard(perms, permission);
    }

    @Override
    public boolean hasPermission(ProxiedPlayer player, String permission) {
        return hasPermission(player.getGameProfile().uuid(), permission);
    }

    @Override
    public void setPermission(UUID playerUuid, String permission, boolean value) {
        Set<String> perms = permissions.computeIfAbsent(playerUuid, _ -> ConcurrentHashMap.newKeySet());
        if (value) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
        markDirty();
    }

    @Override
    public void removePermission(UUID playerUuid, String permission) {
        Set<String> perms = permissions.get(playerUuid);
        if (perms != null) {
            perms.remove(permission);
            markDirty();
        }
    }

    @Override
    public Set<String> getPermissions(UUID playerUuid) {
        Set<String> perms = permissions.get(playerUuid);
        return perms == null ? Collections.emptySet() : Collections.unmodifiableSet(perms);
    }

    @Override
    public void reload() {
        doFlush();
        try {
            load();
        } catch (IOException e) {
            log.error("Failed to reload permissions file", e);
        }
    }

    public static boolean matchWildcard(Set<String> perms, String permission) {
        if (perms == null || perms.isEmpty()) return false;
        if (perms.contains("*")) return true;
        if (perms.contains(permission)) return true;

        boolean explicitDeny = false;
        String denyNode = "-" + permission;
        if (perms.contains(denyNode)) explicitDeny = true;

        for (String p : perms) {
            if (p.endsWith(".*")) {
                String prefix = p.substring(0, p.length() - 2);
                if (permission.startsWith(prefix)) {
                    if (prefix.isEmpty() || permission.startsWith(prefix + ".") || permission.equals(prefix)) {
                        if (explicitDeny) return false;
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
