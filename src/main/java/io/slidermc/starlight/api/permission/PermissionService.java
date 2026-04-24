package io.slidermc.starlight.api.permission;

import io.slidermc.starlight.api.player.ProxiedPlayer;

import java.util.Set;
import java.util.UUID;

public interface PermissionService {

    boolean hasPermission(UUID playerUuid, String permission);

    boolean hasPermission(ProxiedPlayer player, String permission);

    void setPermission(UUID playerUuid, String permission, boolean value);

    void removePermission(UUID playerUuid, String permission);

    Set<String> getPermissions(UUID playerUuid);

    void reload();
}
