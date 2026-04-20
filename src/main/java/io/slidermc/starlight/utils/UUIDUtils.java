package io.slidermc.starlight.utils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UUIDUtils {
    public static UUID generateOfflineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }
}
