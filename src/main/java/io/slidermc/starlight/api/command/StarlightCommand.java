package io.slidermc.starlight.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;

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
    private String description = "";
    private String usage = "";

    protected StarlightCommand(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Command name must not be blank");
        this.name = name.toLowerCase();
    }

    protected StarlightCommand(String name, String description) {
        this(name);
        this.description = description;
    }

    protected StarlightCommand(String name, String description, String usage) {
        this(name, description);
        this.usage = usage;
    }

    /**
     * 构建并返回命令节点。根节点的 literal 名称应与 {@link #getName()} 一致。
     */
    public abstract LiteralArgumentBuilder<IStarlightCommandSource> build();

    public String getName() { return name; }

    /** 命令简介，显示在 /help 列表中。 */
    public String getDescription() { return description; }

    /** 用法说明，如 {@code "/hub"} 或 {@code "/server <name>"}。 */
    public String getUsage() { return usage.isEmpty() ? "/" + name : usage; }

    /** 便捷方法，减少 import 负担。 */
    protected static LiteralArgumentBuilder<IStarlightCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}

