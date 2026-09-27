package io.slidermc.starlight.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import net.kyori.adventure.key.Key;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 代理命令的抽象基类。
 *
 * <p>插件继承此类并实现 {@link #build()} 来定义命令结构：
 * <pre>{@code
 * public class HubCommand extends StarlightCommand {
 *     public HubCommand() { super(CommandMeta.builder("myplugin", "hub").build()); }
 *
 *     @Override
 *     public LiteralArgumentBuilder<IStarlightCommandSource> build() {
 *         return literal(getName())
 *             .executes(ctx -> {
 *                 ctx.getSource().sendMessage(Component.text("Teleporting to hub..."));
 *                 return 1;
 *             });
 *     }
 * }
 * }</pre>
 *
 * <p>注册时：
 * <pre>{@code
 * proxy.getCommandManager().register(new HubCommand());
 * }</pre>
 */
public abstract class StarlightCommand {
    private final Key key;
    private final String description;
    private final String usage;
    private final boolean descriptionAsKey;
    private final boolean usageAsKey;
    /** 由外部框架预先构建的根节点；非 {@code null} 时 {@link #build()} 直接返回它。 */
    private final LiteralArgumentBuilder<IStarlightCommandSource> prebuiltNode;
    private final Set<String> aliases = new LinkedHashSet<>();

    protected StarlightCommand(CommandMeta meta) {
        this(meta, null);
    }

    /**
     * 包装一个已经构建好的 Brigadier 节点。
     *
     * <p>用于把外部命令框架（如 Cloud）生成的节点接入代理命令系统：
     * 节点本身由该框架负责构建，{@link #build()} 直接返回它，不再调用子类的构建逻辑。
     *
     * @param meta          命令元数据
     * @param prebuiltNode  已构建的根节点；为 {@code null} 时退回抽象的 {@link #build()}
     */
    protected StarlightCommand(CommandMeta meta, LiteralArgumentBuilder<IStarlightCommandSource> prebuiltNode) {
        this.key = meta.key();
        this.description = meta.description();
        this.usage = meta.usage();
        this.descriptionAsKey = meta.descriptionAsKey();
        this.usageAsKey = meta.usageAsKey();
        this.prebuiltNode = prebuiltNode;
        this.aliases.addAll(meta.aliases());
    }

    /**
     * 构建并返回命令节点。根节点的 literal 名称应与 {@link #getName()} 一致。
     *
     * <p>若本实例是通过 {@link #StarlightCommand(CommandMeta, LiteralArgumentBuilder)} 创建的
     * 包装器，则直接返回预先构建的节点。
     */
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        if (prebuiltNode == null) {
            throw new UnsupportedOperationException(
                    "Command '" + getName() + "' neither provides a prebuilt node nor overrides build()");
        }
        return prebuiltNode;
    }

    public Key getKey() { return key; }

    /** 全名（namespace:name），用于 Brigadier 根字面量。 */
    public String getName() { return key.asString(); }

    /** 短名（仅 name 部分），用于显示。 */
    public String getDisplayName() { return key.value(); }

    public String getNamespace() { return key.namespace(); }

    /** 命令简介，显示在 /help 列表中。若 {@link #isDescriptionKey()} 为 {@code true}，则该值是一个翻译键。 */
    public String getDescription() { return description; }

    /** 用法说明，如 {@code "/hub"} 或 {@code "/server <name>"}。若 {@link #isUsageKey()} 为 {@code true}，则该值是一个翻译键。 */
    public String getUsage() { return usage.isEmpty() ? "/" + getDisplayName() : usage; }

    /** 若为 {@code true}，{@link #getDescription()} 返回的是翻译键而非原文。 */
    public boolean isDescriptionKey() { return descriptionAsKey; }

    /** 若为 {@code true}，{@link #getUsage()} 返回的是翻译键而非原文。 */
    public boolean isUsageKey() { return usageAsKey; }

    /** 命令别名列表，不可修改。 */
    public Set<String> getAliases() { return Collections.unmodifiableSet(aliases); }

    /** 便捷方法，减少 import 负担。 */
    protected static LiteralArgumentBuilder<IStarlightCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}

