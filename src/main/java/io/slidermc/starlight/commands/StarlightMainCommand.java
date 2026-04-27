package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.CommandMeta;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.plugin.PluginDescription;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Collection;
import java.util.List;

/**
 * {@code /starlight} 主命令，提供版本、插件列表和帮助信息查询。
 */
public class StarlightMainCommand extends StarlightCommand {

    private final StarlightProxy proxy;

    private static final Component BRAND =
            MiniMessageUtils.MINI_MESSAGE.deserialize("<gradient:#FFE100:#C8A200>Starlight</gradient>");

    public StarlightMainCommand(StarlightProxy proxy) {
        super(CommandMeta.builder("starlight")
                .description("starlight.command.starlight.desc", true)
                .usage("starlight.command.starlight.usage", true)
                .build());
        this.proxy = proxy;
    }

    private static final String[][] HELP_ENTRIES = {
            {"version", "starlight.command.starlight.help.desc.version"},
            {"plugins", "starlight.command.starlight.help.desc.plugins"},
            {"commands", "starlight.command.starlight.help.desc.commands"},
            {"help", "starlight.command.starlight.help.desc.help"},
            {"shutdown", "starlight.command.starlight.help.desc.shutdown"},
    };

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
                .executes(ctx -> {
                    sendBrief(ctx.getSource());
                    return 1;
                })
                .then(literal("version")
                        .requires(commandSource -> commandSource.hasPermission("starlight.version"))
                        .executes(ctx -> {
                            sendVersion(ctx.getSource());
                            return 1;
                        }))
                .then(literal("plugins")
                        .requires(commandSource -> commandSource.hasPermission("starlight.plugins"))
                        .executes(ctx -> {
                            sendPlugins(ctx.getSource());
                            return 1;
                        }))
                .then(literal("help")
                        .requires(commandSource -> commandSource.hasPermission("starlight.help"))
                        .executes(ctx -> {
                            sendHelp(ctx.getSource());
                            return 1;
                        }))
                .then(literal("commands")
                        .requires(src -> src.hasPermission("starlight.commands"))
                        .executes(ctx -> {
                            sendCommands(ctx.getSource());
                            return 1;
                        })
                        .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    proxy.getCommandManager().getCommands()
                                            .forEach(cmd -> builder.suggest(cmd.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    sendCommandDetail(ctx.getSource(), name);
                                    return 1;
                                })))
                .then(literal("shutdown")
                        .requires(src -> src.hasPermission("starlight.shutdown"))
                        .executes(ctx -> {
                            ctx.getSource().sendMessage(
                                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                                            t(ctx.getSource(), "starlight.command.starlight.shutdown.stopping")));
                            proxy.shutdown();
                            return 1;
                        }));
    }

    private void sendBrief(IStarlightCommandSource src) {
        src.sendMessage(BRAND.append(
                MiniMessageUtils.MINI_MESSAGE.deserialize(
                        t(src, "starlight.command.starlight.brief"),
                        Placeholder.parsed("version", InternalConfig.VERSION_STRING))));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.hint")));
    }

    private void sendVersion(IStarlightCommandSource src) {
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.version.header")));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.version.version"),
                Placeholder.parsed("version", InternalConfig.VERSION_STRING)));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.version.address"),
                Placeholder.parsed("host", proxy.getAddress().getHostString()),
                Placeholder.parsed("port", String.valueOf(proxy.getAddress().getPort()))));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.version.players"),
                Placeholder.parsed("count", String.valueOf(proxy.getPlayerManager().getPlayers().size()))));
    }

    private void sendPlugins(IStarlightCommandSource src) {
        List<PluginDescription> plugins = proxy.getPluginManager().getLoadedPlugins();
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.plugins.header"),
                Placeholder.parsed("count", String.valueOf(plugins.size()))));
        if (plugins.isEmpty()) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.starlight.plugins.empty")));
        } else {
            for (PluginDescription desc : plugins) {
                Component hover = buildPluginHover(desc, src);
                Component entry = MiniMessageUtils.MINI_MESSAGE.deserialize(
                        t(src, "starlight.command.starlight.plugins.entry"),
                        Placeholder.parsed("name", desc.name()),
                        Placeholder.parsed("version", desc.version()))
                        .hoverEvent(HoverEvent.showText(hover));
                src.sendMessage(entry);
            }
        }
    }

    private Component buildPluginHover(PluginDescription desc, IStarlightCommandSource src) {
        Component result = MiniMessageUtils.MINI_MESSAGE.deserialize(
                "<white><bold>" + desc.name() + "</bold></white> <dark_gray>v" + desc.version() + "</dark_gray>");

        if (desc.description() != null && !desc.description().isBlank()) {
            result = result.append(Component.newline())
                    .append(MiniMessageUtils.MINI_MESSAGE.deserialize(
                            "<gray>" + desc.description() + "</gray>"));
        }

        if (!desc.authors().isEmpty()) {
            result = result.append(Component.newline())
                    .append(MiniMessageUtils.MINI_MESSAGE.deserialize(
                            t(src, "starlight.command.starlight.plugins.hover.authors"),
                            Placeholder.parsed("authors", String.join(", ", desc.authors()))));
        }

        if (!desc.depends().isEmpty()) {
            result = result.append(Component.newline())
                    .append(MiniMessageUtils.MINI_MESSAGE.deserialize(
                            t(src, "starlight.command.starlight.plugins.hover.depends"),
                            Placeholder.parsed("depends", String.join(", ", desc.depends()))));
        }

        if (!desc.softDepends().isEmpty()) {
            result = result.append(Component.newline())
                    .append(MiniMessageUtils.MINI_MESSAGE.deserialize(
                            t(src, "starlight.command.starlight.plugins.hover.soft_depends"),
                            Placeholder.parsed("soft_depends", String.join(", ", desc.softDepends()))));
        }

        return result;
    }

    private void sendHelp(IStarlightCommandSource src) {
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.help.header")));
        for (String[] entry : HELP_ENTRIES) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.starlight.help.entry"),
                    Placeholder.parsed("usage", "/starlight " + entry[0]),
                    Placeholder.parsed("description", t(src, entry[1]))));
        }
    }

    private void sendCommands(IStarlightCommandSource src) {
        Collection<StarlightCommand> commands = proxy.getCommandManager().getCommands();
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.commands.header")));
        for (StarlightCommand cmd : commands) {
            String usage = cmd.isUsageKey() ? t(src, cmd.getUsage()) : cmd.getUsage();
            String desc = cmd.isDescriptionKey() ? t(src, cmd.getDescription()) : cmd.getDescription();
            String entryKey = desc.isEmpty()
                    ? "starlight.command.starlight.commands.entry_no_desc"
                    : "starlight.command.starlight.commands.entry";
            Component entry = MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, entryKey),
                    Placeholder.parsed("name", cmd.getName()),
                    Placeholder.parsed("description", desc))
                    .hoverEvent(HoverEvent.showText(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    t(src, "starlight.command.starlight.commands.hover_usage"),
                                    Placeholder.parsed("usage", usage))));
            src.sendMessage(entry);
        }
    }

    private void sendCommandDetail(IStarlightCommandSource src, String name) {
        StarlightCommand cmd = proxy.getCommandManager().getCommand(name);
        if (cmd == null) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.starlight.commands.not_found"),
                    Placeholder.parsed("name", name)));
            return;
        }
        String usage = cmd.isUsageKey() ? t(src, cmd.getUsage()) : cmd.getUsage();
        String desc = cmd.isDescriptionKey() ? t(src, cmd.getDescription()) : cmd.getDescription();
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.commands.detail.header"),
                Placeholder.parsed("name", cmd.getName())));
        if (!desc.isEmpty()) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.starlight.commands.detail.desc"),
                    Placeholder.parsed("description", desc)));
        }
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.starlight.commands.detail.usage"),
                Placeholder.parsed("usage", usage)));
    }

    /**
     * 翻译辅助方法：若 source 是玩家，使用其客户端 locale；否则使用代理默认 locale。
     */
    private String t(IStarlightCommandSource src, String key) {
        return src.asProxiedPlayer()
                .map(p -> p.getConnectionContext().getTranslation(key))
                .orElseGet(() -> proxy.getTranslateManager().translate(key));
    }
}
