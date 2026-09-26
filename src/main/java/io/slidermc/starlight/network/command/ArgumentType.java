package io.slidermc.starlight.network.command;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令参数类型
 *
 * <p>对应 Minecraft 的 {@code minecraft:command_argument_type} 注册表。此枚举的声明顺序即为
 * Starlight 内部的"语义 ID"（{@link #ordinal()}），与任何具体协议版本的线上 ID 无关。
 * 线上 ID 与语义 ID 的对应关系由 {@code resources/data/command_argument_types/<协议版本>.json}
 * 描述，并在启动时由 {@link CommandArgumentTypeRegistry} 加载。
 *
 * <p>之所以需要这一层间接：Minecraft 每次新增参数类型都会改变其后所有类型的线上 ID。
 * 例如 {@code dialog} 与 {@code uuid} 在协议 775/776 中分别是 55 和 56，而 26.3（协议 777）
 * 在它们之前插入了 5 个新类型，于是变成了 58 和 61。用语义 ID + 每版本映射表可以在
 * 不改动任何解析逻辑的前提下支持这些位移。
 *
 * <p>{@link #bracketed()} 表示该类型在协议中的表现：凡是带属性字段的类型都必须逐字段读写，
 * 其余类型在线上只占一个参数类型 ID，没有任何附加字节。
 */
public enum ArgumentType {
    // Brigadier 基础类型
    BRIGADIER_BOOL("brigadier:bool", false),
    BRIGADIER_FLOAT("brigadier:float", true),
    BRIGADIER_DOUBLE("brigadier:double", true),
    BRIGADIER_INTEGER("brigadier:integer", true),
    BRIGADIER_LONG("brigadier:long", true),
    BRIGADIER_STRING("brigadier:string", true),

    // Minecraft 特定类型
    ENTITY("entity", true),
    GAME_PROFILE("game_profile", false),
    BLOCK_POS("block_pos", false),
    COLUMN_POS("column_pos", false),
    VEC3("vec3", false),
    VEC2("vec2", false),
    BLOCK_STATE("block_state", false),
    BLOCK_PREDICATE("block_predicate", false),
    ITEM_STACK("item_stack", false),
    ITEM_PREDICATE("item_predicate", false),
    /**
     * 队伍颜色参数。
     * 1.21.11 与 26.1 的注册名为 {@code color}，26.2 起改名为 {@code team_color}，线上格式不变。
     */
    TEAM_COLOR("team_color", false),
    HEX_COLOR("hex_color", false),
    COMPONENT("component", false),
    STYLE("style", false),
    MESSAGE("message", false),
    NBT_COMPOUND_TAG("nbt_compound_tag", false),
    NBT_TAG("nbt_tag", false),
    NBT_PATH("nbt_path", false),
    OBJECTIVE("objective", false),
    OBJECTIVE_CRITERIA("objective_criteria", false),
    OPERATION("operation", false),
    PARTICLE("particle", false),
    ANGLE("angle", false),
    ROTATION("rotation", false),
    SCOREBOARD_SLOT("scoreboard_slot", false),
    SCORE_HOLDER("score_holder", true),
    SWIZZLE("swizzle", false),
    TEAM("team", false),
    ITEM_SLOT("item_slot", false),
    ITEM_SLOTS("item_slots", false),
    RESOURCE_LOCATION("resource_location", false),
    FUNCTION("function", false),
    ENTITY_ANCHOR("entity_anchor", false),
    INT_RANGE("int_range", false),
    FLOAT_RANGE("float_range", false),
    DIMENSION("dimension", false),
    GAMEMODE("gamemode", false),
    TIME("time", true),
    RESOURCE_OR_TAG("resource_or_tag", true),
    RESOURCE_OR_TAG_KEY("resource_or_tag_key", true),
    RESOURCE("resource", true),
    RESOURCE_KEY("resource_key", true),
    RESOURCE_SELECTOR("resource_selector", true),
    TEMPLATE_MIRROR("template_mirror", false),
    TEMPLATE_ROTATION("template_rotation", false),
    HEIGHTMAP("heightmap", false),
    LOOT_TABLE("loot_table", false),
    LOOT_PREDICATE("loot_predicate", false),
    LOOT_MODIFIER("loot_modifier", false),

    // 以下类型在 26.1/26.2 中并不存在（或位置不同），它们只会出现在包含它们的协议版本映射文件中
    DIALOG("dialog", false),
    UUID("uuid", false),
    CONTEXT_FLOAT_PROVIDER("context_float_provider", false),
    CONTEXT_INT_PROVIDER("context_int_provider", false),
    SLOT_SOURCE("slot_source", false),
    FEATURE("feature", false),
    SWING_ANIMATION("swing_animation", false);

    private final String key;
    private final boolean bracketed;

    ArgumentType(String key, boolean bracketed) {
        this.key = key;
        this.bracketed = bracketed;
    }

    /**
     * 此类型在网络协议中是否携带附加属性字段。
     *
     * @return 需要逐字段读写属性时为 {@code true}，仅有一个类型 ID 时为 {@code false}
     */
    public boolean bracketed() {
        return bracketed;
    }

    /**
     * 注册名，与 Minecraft 的 {@code minecraft:command_argument_type} 注册键一致。
     *
     * @return 注册名（不含 {@code minecraft:} 命名空间前缀）
     */
    public String key() {
        return key;
    }

    private static final Map<String, ArgumentType> BY_KEY = new HashMap<>();

    /**
     * 历史注册名别名。
     *
     * <p>同一个参数类型的注册名在不同版本之间可能被重命名，但线上格式不变，
     * 因此映射文件里出现的旧名字需要能解析到同一个枚举常量。
     * 目前只有队伍颜色：协议 774 与 775 中叫 {@code color}，776 起改名为 {@code team_color}。
     */
    private static final Map<String, ArgumentType> ALIASES = Map.of("color", TEAM_COLOR);

    static {
        for (ArgumentType type : values()) {
            ArgumentType previous = BY_KEY.put(type.key, type);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate argument type key '" + type.key + "': " + previous + " and " + type);
            }
        }
    }

    /**
     * 按注册名查找参数类型，同时接受历史注册名别名。
     *
     * @param key 注册名，不含命名空间前缀
     * @return 对应的参数类型，不存在时返回 {@code null}
     */
    public static ArgumentType byKey(String key) {
        ArgumentType type = BY_KEY.get(key);
        return type != null ? type : ALIASES.get(key);
    }
}
