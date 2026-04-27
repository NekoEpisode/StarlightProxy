package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.CommandMeta;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PermCommand extends StarlightCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND =
            new SimpleCommandExceptionType(() -> "Player not found");
    private static final SimpleCommandExceptionType NO_PERMISSION =
            new SimpleCommandExceptionType(() -> "You don't have permission to use this command");

    private final StarlightProxy proxy;

    public PermCommand(StarlightProxy proxy) {
        super(CommandMeta.builder("sperm")
                .description("starlight.command.perm.desc", true)
                .usage("starlight.command.perm.usage", true)
                .build());
        this.proxy = proxy;
    }

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
                .requires(src -> src.hasPermission("starlight.perm"))
                .then(literal("reload").executes(this::executeReload))
                .then(literal("user")
                        .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("target", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                .then(literal("add")
                                        .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("permission", StringArgumentType.greedyString())
                                                .executes(this::executeUserAdd)))
                                .then(literal("remove")
                                        .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("permission", StringArgumentType.greedyString())
                                                .executes(this::executeUserRemove)))
                                .then(literal("list")
                                        .executes(this::executeUserList))))
                .then(literal("help").executes(this::executeHelp))
                .executes(this::executeHelp);
    }

    private record ResolvedTarget(UUID uuid, String username) {}

    private ResolvedTarget resolveTarget(String input) throws CommandSyntaxException {
        try {
            UUID uuid = UUID.fromString(input);
            ProxiedPlayer player = proxy.getPlayerManager().getPlayer(uuid);
            return new ResolvedTarget(uuid, player != null ? player.getGameProfile().username() : null);
        } catch (IllegalArgumentException e) {
            ProxiedPlayer player = proxy.getPlayerManager().getPlayer(input);
            if (player == null) throw PLAYER_NOT_FOUND.create();
            return new ResolvedTarget(player.getGameProfile().uuid(), player.getGameProfile().username());
        }
    }

    private int executeReload(CommandContext<IStarlightCommandSource> ctx) throws CommandSyntaxException {
        requirePermission(ctx);
        proxy.getPermissionService().reload();
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.reloaded")));
        return 1;
    }

    private int executeUserAdd(CommandContext<IStarlightCommandSource> ctx) throws CommandSyntaxException {
        requirePermission(ctx);
        String target = StringArgumentType.getString(ctx, "target");
        String permission = StringArgumentType.getString(ctx, "permission");
        ResolvedTarget resolved = resolveTarget(target);

        proxy.getPermissionService().setPermission(resolved.uuid(), permission, true);

        String displayName = resolved.username() != null ? resolved.username() : resolved.uuid().toString();
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.added"),
                Placeholder.parsed("player", displayName),
                Placeholder.parsed("permission", permission)));

        if (resolved.username() != null) {
            ProxiedPlayer player = proxy.getPlayerManager().getPlayer(resolved.uuid());
            if (player != null) {
                player.getConnectionContext().refreshCommands();
            }
        }
        return 1;
    }

    private int executeUserRemove(CommandContext<IStarlightCommandSource> ctx) throws CommandSyntaxException {
        requirePermission(ctx);
        String target = StringArgumentType.getString(ctx, "target");
        String permission = StringArgumentType.getString(ctx, "permission");
        ResolvedTarget resolved = resolveTarget(target);

        proxy.getPermissionService().removePermission(resolved.uuid(), permission);

        String displayName = resolved.username() != null ? resolved.username() : resolved.uuid().toString();
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.removed"),
                Placeholder.parsed("player", displayName),
                Placeholder.parsed("permission", permission)));

        if (resolved.username() != null) {
            ProxiedPlayer player = proxy.getPlayerManager().getPlayer(resolved.uuid());
            if (player != null) {
                player.getConnectionContext().refreshCommands();
            }
        }
        return 1;
    }

    private int executeUserList(CommandContext<IStarlightCommandSource> ctx) throws CommandSyntaxException {
        requirePermission(ctx);
        String target = StringArgumentType.getString(ctx, "target");
        ResolvedTarget resolved = resolveTarget(target);

        String displayName = resolved.username() != null ? resolved.username() : resolved.uuid().toString();
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.list.header"),
                Placeholder.parsed("player", displayName)));

        Set<String> perms = proxy.getPermissionService().getPermissions(resolved.uuid());
        if (perms.isEmpty()) {
            ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(ctx.getSource(), "starlight.command.perm.list.empty")));
        } else {
            for (String p : perms) {
                ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                        t(ctx.getSource(), "starlight.command.perm.list.entry"),
                        Placeholder.parsed("permission", p)));
            }
        }
        return 1;
    }

    private int executeHelp(CommandContext<IStarlightCommandSource> ctx) {
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.help.header")));
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.help.reload")));
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.help.user_add")));
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.help.user_remove")));
        ctx.getSource().sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(ctx.getSource(), "starlight.command.perm.help.user_list")));
        return 1;
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<IStarlightCommandSource> ctx, SuggestionsBuilder builder) {
        for (ProxiedPlayer player : proxy.getPlayerManager().getPlayers()) {
            builder.suggest(player.getGameProfile().username());
        }
        return builder.buildFuture();
    }

    private void requirePermission(CommandContext<IStarlightCommandSource> ctx) throws CommandSyntaxException {
        if (!ctx.getSource().hasPermission("starlight.perm")) {
            throw NO_PERMISSION.create();
        }
    }

    private String t(IStarlightCommandSource src, String key) {
        return src.asProxiedPlayer()
                .map(p -> p.getConnectionContext().getTranslation(key))
                .orElseGet(() -> proxy.getTranslateManager().translate(key));
    }
}
