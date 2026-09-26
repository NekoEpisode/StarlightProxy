package io.slidermc.starlight.network.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令参数类型注册表
 *
 * <p>负责加载 {@code resources/data/command_argument_types} 目录下以协议版本号命名的 JSON 文件，
 * 并建立"协议版本 + 线上参数类型 ID"与 {@link ArgumentType} 语义 ID（{@link ArgumentType#ordinal()}）
 * 之间的双向对照表。
 *
 * <p>JSON 文件由 Minecraft 服务端的 Data Generator 输出的人工整理版本，格式为：
 * <pre>{@code
 * {
 *   "types": [
 *     { "name": "brigadier:bool", "protocol_id": 0 },
 *     { "name": "uuid",           "protocol_id": 61 }
 *   ]
 * }
 * }</pre>
 *
 * <p>文件内容必须是<b>完整</b>的注册表快照：下标即线上 ID，因此 {@code protocol_id} 必须从 0 开始
 * 连续且不重复。校验失败会在启动阶段抛出 {@link IllegalStateException}，避免带着错误的映射表运行
 * 而把玩家客户端的命令树解析成乱码。
 *
 * <p>某个协议版本没有对应文件时，该版本的参数类型<b>不会被猜测</b>：{@link #read(int, int)} 与
 * {@link #write(int, ArgumentType)} 会直接抛异常，启动时也会给出警告。因为 {@link ArgumentType}
 * 的声明顺序是 Starlight 的内部语义 ID（含版本相关类型），拿它当线路 ID 表用会静默解析出错误的
 * 参数类型，危害远大于显式失败。
 *
 * <p>线程安全：所有对照表在启动时一次性构建，之后只读；{@link #loadMappings()} 必须在服务器开始
 * 接受连接之前调用完毕。
 */
public class CommandArgumentTypeRegistry {
    private static final Logger log = LoggerFactory.getLogger(CommandArgumentTypeRegistry.class);

    private static final String MAPPING_DIR = "data/command_argument_types";

    private final TranslateManager translateManager;

    /** 协议版本 -> 线上 ID 到语义类型的对照表 */
    private final Map<Integer, ArgumentType[]> byProtocolVersion = new ConcurrentHashMap<>();

    /** 协议版本 -> 语义类型到线上 ID 的对照表 */
    private final Map<Integer, int[]> byProtocolVersionReversed = new ConcurrentHashMap<>();

    public CommandArgumentTypeRegistry(TranslateManager translateManager) {
        this.translateManager = translateManager;
    }

    private String t(String key) {
        return translateManager.translate(key);
    }

    /**
     * 加载 {@code data/command_argument_types} 下的全部映射文件。
     *
     * @throws IllegalStateException 当任一映射文件缺失、格式错误或内容不合法时
     */
    public void loadMappings() {
        long start = System.currentTimeMillis();
        log.info(t("starlight.logging.info.argument_type.loading"));

        List<String> paths = ResourceUtil.listFiles(MAPPING_DIR, "json");
        Set<Integer> loaded = new HashSet<>();
        Map<Integer, ArgumentType[]> parsed = new HashMap<>();

        for (String path : paths) {
            // listFiles 返回的 path 可能是 "775.json" 或 "/775.json"，统一处理
            String fileName = path.startsWith("/") ? path.substring(1) : path;
            if (!fileName.endsWith(".json")) {
                continue;
            }

            int protocolVersion;
            try {
                protocolVersion = Integer.parseInt(fileName.substring(0, fileName.length() - ".json".length()));
            } catch (NumberFormatException e) {
                // 命名格式错误属于打包/维护错误，直接失败比静默跳过更安全
                throw new IllegalStateException("Argument type mapping file name is not a protocol version: " + fileName, e);
            }

            String fullResourcePath = MAPPING_DIR + "/" + fileName;
            try (InputStream inputStream = CommandArgumentTypeRegistry.class.getClassLoader().getResourceAsStream(fullResourcePath)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Argument type mapping resource not found: " + fullResourcePath);
                }
                ArgumentType[] types = parseMapping(inputStream, fullResourcePath);
                if (parsed.put(protocolVersion, types) != null) {
                    throw new IllegalStateException("Duplicate argument type mapping for protocol version " + protocolVersion);
                }
                loaded.add(protocolVersion);
                log.info(t("starlight.logging.info.argument_type.loaded_version"), protocolVersion, types.length);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load argument type mapping: " + fullResourcePath, e);
            }
        }

        if (parsed.isEmpty()) {
            throw new IllegalStateException("No command argument type mapping was loaded from " + MAPPING_DIR);
        }

        for (Map.Entry<Integer, ArgumentType[]> entry : parsed.entrySet()) {
            register(entry.getKey(), entry.getValue());
        }

        for (ProtocolVersion version : ProtocolVersion.values()) {
            int code = version.getProtocolVersionCode();
            if (code < 0) {
                continue;
            }
            if (loaded.contains(code)) {
                continue;
            }
            log.warn(t("starlight.logging.warn.argument_type.missing_version"), version.name(), code, code);
        }

        log.info(t("starlight.logging.info.argument_type.load_complete"), loaded.size(), System.currentTimeMillis() - start);
    }

    /**
     * 解析单个映射文件。
     *
     * @param inputStream 资源输入流
     * @param sourceName  资源路径，仅用于错误信息
     * @return 下标为线上 ID 的类型数组
     */
    private ArgumentType[] parseMapping(InputStream inputStream, String sourceName) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IllegalStateException(sourceName + ": root element must be a JSON object");
            }
            JsonElement typesElement = root.getAsJsonObject().get("types");
            if (typesElement == null || !typesElement.isJsonArray()) {
                throw new IllegalStateException(sourceName + ": missing \"types\" array");
            }

            JsonArray types = typesElement.getAsJsonArray();
            List<ArgumentType> sorted = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) {
                sorted.add(null);
            }
            Set<String> seenNames = new HashSet<>();

            for (JsonElement element : types) {
                if (!element.isJsonObject()) {
                    throw new IllegalStateException(sourceName + ": every entry of \"types\" must be a JSON object");
                }
                JsonObject entry = element.getAsJsonObject();
                JsonElement nameElement = entry.get("name");
                JsonElement idElement = entry.get("protocol_id");
                if (nameElement == null || idElement == null) {
                    throw new IllegalStateException(sourceName + ": every entry needs both \"name\" and \"protocol_id\"");
                }

                String name = nameElement.getAsString();
                int protocolId = idElement.getAsInt();
                if (protocolId < 0 || protocolId >= types.size()) {
                    throw new IllegalStateException(sourceName + ": protocol_id " + protocolId + " of '" + name
                            + "' is out of range 0.." + (types.size() - 1));
                }

                ArgumentType type = ArgumentType.byKey(name);
                if (type == null) {
                    throw new IllegalStateException(sourceName + ": unknown argument type '" + name
                            + "', add it to " + ArgumentType.class.getSimpleName() + " first");
                }
                if (!seenNames.add(name)) {
                    throw new IllegalStateException(sourceName + ": duplicated argument type '" + name + "'");
                }
                if (sorted.get(protocolId) != null) {
                    throw new IllegalStateException(sourceName + ": protocol_id " + protocolId + " is used by both '"
                            + sorted.get(protocolId).key() + "' and '" + name + "'");
                }
                sorted.set(protocolId, type);
            }

            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i) == null) {
                    throw new IllegalStateException(sourceName + ": protocol_id " + i + " is empty, "
                            + "the mapping must describe the complete registry");
                }
            }
            return sorted.toArray(new ArgumentType[0]);
        }
    }

    /**
     * 登记一个协议版本的对照表。
     *
     * @param protocolVersion 协议版本号
     * @param types           下标为线上 ID 的类型数组
     */
    private void register(int protocolVersion, ArgumentType[] types) {
        int[] reversed = new int[ArgumentType.values().length];
        Arrays.fill(reversed, -1);
        for (int protocolId = 0; protocolId < types.length; protocolId++) {
            reversed[types[protocolId].ordinal()] = protocolId;
        }

        byProtocolVersion.put(protocolVersion, types);
        byProtocolVersionReversed.put(protocolVersion, reversed);
    }

    /**
     * 判断某个协议版本是否已加载映射。
     *
     * @param protocolVersion 协议版本号
     * @return 已加载时为 {@code true}
     */
    public boolean isSupported(int protocolVersion) {
        return byProtocolVersion.containsKey(protocolVersion);
    }

    /**
     * 获取某个协议版本的完整类型表。
     *
     * <p>主要用于诊断与验证：返回值下标即该版本的线上参数类型 ID。
     *
     * @param protocolVersion 协议版本号
     * @return 下标为线上 ID 的类型数组副本，该版本没有映射时返回 {@code null}
     */
    public ArgumentType[] getTypes(int protocolVersion) {
        ArgumentType[] types = byProtocolVersion.get(protocolVersion);
        return types == null ? null : types.clone();
    }

    /**
     * 将线上参数类型 ID 转换为语义类型。
     *
     * @param protocolVersion 协议版本号
     * @param protocolId      线上参数类型 ID
     * @return 对应的语义类型
     * @throws IllegalArgumentException 该协议版本没有映射，或该 ID 在此协议版本中不存在时
     */
    public ArgumentType read(int protocolVersion, int protocolId) {
        ArgumentType[] types = requireMapping(protocolVersion);
        if (protocolId < 0 || protocolId >= types.length) {
            throw new IllegalArgumentException(
                    "Unknown parser ID: " + protocolId + " (protocol " + protocolVersion + ")");
        }
        return types[protocolId];
    }

    /**
     * 将语义类型转换为线上参数类型 ID。
     *
     * @param protocolVersion 协议版本号
     * @param type            语义类型
     * @return 线上参数类型 ID
     * @throws IllegalArgumentException 该协议版本没有映射，或该类型在此协议版本中不存在时
     */
    public int write(int protocolVersion, ArgumentType type) {
        int[] reversed = requireReversedMapping(protocolVersion);
        int protocolId = reversed[type.ordinal()];
        if (protocolId < 0) {
            throw new IllegalArgumentException(
                    "Argument type " + type.key() + " does not exist in protocol " + protocolVersion);
        }
        return protocolId;
    }

    /**
     * 取某个协议版本的类型表，没有映射时拒绝处理。
     *
     * <p>这里刻意不做"按枚举顺序猜测"的兜底：{@link ArgumentType} 的声明顺序是 Starlight 的内部
     * 语义 ID，其中包含只在特定版本存在的类型，把它直接当作线路 ID 表会把参数类型解析成完全错误的
     * 类型（例如把 26.3 的 {@code feature} 解析成 {@code dialog}）。错误的命令树会让客户端在补全或
     * 执行命令时出现难以排查的问题，因此宁可显式失败。
     *
     * @param protocolVersion 协议版本号
     * @return 该协议版本的类型表
     * @throws IllegalArgumentException 该协议版本没有映射文件时
     */
    private ArgumentType[] requireMapping(int protocolVersion) {
        ArgumentType[] types = byProtocolVersion.get(protocolVersion);
        if (types == null) {
            throw new IllegalArgumentException("No command argument type mapping loaded for protocol " + protocolVersion);
        }
        return types;
    }

    /**
     * 取某个协议版本的语义类型到线上 ID 的反查表，没有映射时拒绝处理。
     *
     * @param protocolVersion 协议版本号
     * @return 该协议版本的反查表
     * @throws IllegalArgumentException 该协议版本没有映射文件时
     */
    private int[] requireReversedMapping(int protocolVersion) {
        int[] reversed = byProtocolVersionReversed.get(protocolVersion);
        if (reversed == null) {
            throw new IllegalArgumentException("No command argument type mapping loaded for protocol " + protocolVersion);
        }
        return reversed;
    }
}
