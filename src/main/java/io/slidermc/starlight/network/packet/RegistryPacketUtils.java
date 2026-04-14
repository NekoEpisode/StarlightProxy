package io.slidermc.starlight.network.packet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.utils.ResourceUtil;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class RegistryPacketUtils {
    private static final Logger log = LoggerFactory.getLogger(RegistryPacketUtils.class);
    private final PacketRegistry packetRegistry;
    private final TranslateManager translateManager;
    private final Map<Key, List<PacketIdWithProtocolVersion>> mapping = new ConcurrentHashMap<>();

    public RegistryPacketUtils(PacketRegistry packetRegistry, TranslateManager translateManager) {
        this.packetRegistry = packetRegistry;
        this.translateManager = translateManager;
    }

    /** 翻译指定键。 */
    private String t(String key) {
        return translateManager.translate(key);
    }

    public void loadMappings() {
        long start = System.currentTimeMillis();
        log.info(t("starlight.logging.info.packet.loading"));
        List<String> paths = ResourceUtil.listFiles("data/packets", "json");
        for (String path : paths) {
            // listFiles 返回的 path 可能是 "775.json" 或 "/775.json"，统一处理
            String fileName = path.startsWith("/") ? path.substring(1) : path;
            String fullResourcePath = "data/packets/" + fileName;

            // 从文件名中提取协议版本号(如 "775.json" → 775)
            int protocolVersion;
            try {
                protocolVersion = Integer.parseInt(fileName.replace(".json", ""));
            } catch (NumberFormatException e) {
                log.warn(t("starlight.logging.warn.packet.skip_non_numeric"), fileName);
                continue;
            }

            try (InputStream inputStream = RegistryPacketUtils.class.getClassLoader().getResourceAsStream(fullResourcePath)) {
                if (inputStream == null) {
                    log.warn(t("starlight.logging.warn.packet.load_not_found"), fullResourcePath);
                    continue;
                }
                parsePacketMappingJson(inputStream, protocolVersion);
                log.info(t("starlight.logging.info.packet.loaded_version"), protocolVersion);
            } catch (IOException e) {
                log.error(t("starlight.logging.error.packet.load_failed"), fullResourcePath, e);
            }
        }
        log.info(t("starlight.logging.info.packet.load_complete"), (System.currentTimeMillis() - start));
    }

    /**
     * 解析单个 JSON 文件，将其中所有包名 → (protocolVersion, state, direction, packetId) 写入 mapping。
     */
    private void parsePacketMappingJson(InputStream inputStream, int protocolVersion) {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String stateName : root.keySet()) {
                ProtocolState state = parseState(stateName);
                if (state == null) {
                    log.warn(t("starlight.logging.warn.packet.unknown_state"), stateName);
                    continue;
                }
                JsonObject stateObj = root.getAsJsonObject(stateName);
                for (String directionName : stateObj.keySet()) {
                    ProtocolDirection direction = parseDirection(directionName);
                    if (direction == null) {
                        log.warn(t("starlight.logging.warn.packet.unknown_direction"), directionName);
                        continue;
                    }
                    JsonObject directionObj = stateObj.getAsJsonObject(directionName);
                    for (String packetName : directionObj.keySet()) {
                        int packetId = directionObj.getAsJsonObject(packetName).get("protocol_id").getAsInt();
                        Key key = Key.key(packetName);
                        mapping.computeIfAbsent(key, k -> new ArrayList<>())
                               .add(new PacketIdWithProtocolVersion(packetId, protocolVersion, state, direction));
                    }
                }
            }
        } catch (IOException e) {
            log.error(t("starlight.logging.error.packet.parse_failed"), e);
        }
    }

    /**
     * 根据 Key 从 mapping 中查找所有匹配条目，并向 packetRegistry 注册对应的包工厂。
     */
    public void registerByAutoMapping(Key key, Supplier<? extends IMinecraftPacket> supplier) {
        List<PacketIdWithProtocolVersion> entries = mapping.get(key);
        if (entries == null || entries.isEmpty()) {
            log.warn(t("starlight.logging.warn.packet.no_mapping"), key);
            return;
        }
        for (PacketIdWithProtocolVersion entry : entries) {
            packetRegistry.registerPacket(entry.protocolVersion(), entry.state(), entry.direction(), entry.packetId(), supplier);
        }
    }

    /**
     * 根据 Key 从 mapping 中查找所有匹配条目，并从 packetRegistry 中注销。
     */
    public void unregisterByAutoMapping(Key key) {
        List<PacketIdWithProtocolVersion> entries = mapping.get(key);
        if (entries == null || entries.isEmpty()) {
            log.warn(t("starlight.logging.warn.packet.no_mapping"), key);
            return;
        }
        for (PacketIdWithProtocolVersion entry : entries) {
            packetRegistry.unregisterPacket(entry.protocolVersion(), entry.state(), entry.direction(), entry.packetId());
        }
    }

    /**
     * 根据 Key + 版本/状态/方向，从 packetRegistry 创建包实例。
     */
    public IMinecraftPacket createPacket(Key key, int protocolVersion, ProtocolState state, ProtocolDirection direction) {
        int packetId = resolvePacketId(key, protocolVersion, state, direction);
        return packetRegistry.createPacket(protocolVersion, state, direction, packetId);
    }

    /**
     * 根据 Key + 版本/状态/方向，检查 packetRegistry 中是否存在对应包。
     */
    public boolean hasPacket(Key key, int protocolVersion, ProtocolState state, ProtocolDirection direction) {
        List<PacketIdWithProtocolVersion> entries = mapping.get(key);
        if (entries == null) return false;
        for (PacketIdWithProtocolVersion entry : entries) {
            if (entry.protocolVersion() == protocolVersion
                    && entry.state() == state
                    && entry.direction() == direction) {
                return packetRegistry.hasPacket(protocolVersion, state, direction, entry.packetId());
            }
        }
        return false;
    }

    /**
     * 从 mapping 中查找与指定版本/状态/方向匹配的 packetId。
     */
    private int resolvePacketId(Key key, int protocolVersion, ProtocolState state, ProtocolDirection direction) {
        List<PacketIdWithProtocolVersion> entries = mapping.get(key);
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("No mapping found for key: " + key);
        }
        for (PacketIdWithProtocolVersion entry : entries) {
            if (entry.protocolVersion() == protocolVersion
                    && entry.state() == state
                    && entry.direction() == direction) {
                return entry.packetId();
            }
        }
        throw new IllegalArgumentException(
                "No mapping found for key: " + key
                + ", protocolVersion=" + protocolVersion
                + ", state=" + state
                + ", direction=" + direction
        );
    }

    public PacketRegistry getPacketRegistry() {
        return packetRegistry;
    }

    private static ProtocolState parseState(String name) {
        return switch (name.toLowerCase()) {
            case "handshake"      -> ProtocolState.HANDSHAKE;
            case "status"         -> ProtocolState.STATUS;
            case "login"          -> ProtocolState.LOGIN;
            case "configuration"  -> ProtocolState.CONFIGURATION;
            case "play"           -> ProtocolState.PLAY;
            default               -> null;
        };
    }

    private static ProtocolDirection parseDirection(String name) {
        return switch (name.toLowerCase()) {
            case "clientbound" -> ProtocolDirection.CLIENTBOUND;
            case "serverbound" -> ProtocolDirection.SERVERBOUND;
            default            -> null;
        };
    }

    private record PacketIdWithProtocolVersion(int packetId, int protocolVersion, ProtocolState state, ProtocolDirection direction) {}
}
