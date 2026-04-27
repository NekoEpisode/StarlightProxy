package io.slidermc.starlight.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 代理命令的抽象基类。
 *
 * <p>插件继承此类并实现 {@link #build()} 来定义命令结构：
 * <pre>{@code
 * public class HubCommand extends StarlightCommand {
 *     public HubCommand() { super("hub"); }
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
    private final String name;
    private final String description;
    private final String usage;
    private final boolean descriptionAsKey;
    private final boolean usageAsKey;
    private final Set<String> aliases = new LinkedHashSet<>();

    protected StarlightCommand(CommandMeta meta) {
        this.name = meta.name();
        this.description = meta.description();
        this.usage = meta.usage();
        this.descriptionAsKey = meta.descriptionAsKey();
        this.usageAsKey = meta.usageAsKey();
        this.aliases.addAll(meta.aliases());
    }

    /**
     * 构建并返回命令节点。根节点的 literal 名称应与 {@link #getName()} 一致。
     */
    public abstract LiteralArgumentBuilder<IStarlightCommandSource> build();

    public String getName() { return name; }

    /** 命令简介，显示在 /help 列表中。若 {@link #isDescriptionKey()} 为 {@code true}，则该值是一个翻译键。 */
    public String getDescription() { return description; }

    /** 用法说明，如 {@code "/hub"} 或 {@code "/server <name>"}。若 {@link #isUsageKey()} 为 {@code true}，则该值是一个翻译键。 */
    public String getUsage() { return usage.isEmpty() ? "/" + name : usage; }

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

