package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Optional;

public class ServerCommand extends StarlightCommand {
    private final StarlightProxy proxy;

    public ServerCommand(StarlightProxy proxy) {
        super("server", "starlight.command.server.desc", "starlight.command.server.usage", true, true);
        this.proxy = proxy;
    }

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
                .requires(commandSource -> commandSource.hasPermission("starlight.server"))
                .executes(ctx -> {
                    String locale = proxy.getTranslateManager().getActiveLocale();
                    Optional<ProxiedPlayer> optPlayer = ctx.getSource().asProxiedPlayer();
                    if (optPlayer.isPresent()) {
                        ConnectionContext context = optPlayer.get().getConnectionContext();
                        locale = context.getLocale();
                    }
                    String usage = isUsageKey() ? proxy.getTranslateManager().translate(locale, getUsage()) : getUsage();
                    ctx.getSource().sendMessage(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    proxy.getTranslateManager().translate(locale, "starlight.command.usage"),
                                    Placeholder.parsed("usage", usage)
                            )
                    );
                    return 0;
                })
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                        .<IStarlightCommandSource, String>argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().asProxiedPlayer().ifPresent(player ->
                                    player.getProxy().getServerManager().getServers()
                                            .forEach(s -> builder.suggest(s.getName()))
                            );
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String serverName = StringArgumentType.getString(ctx, "name");
                            ctx.getSource().asProxiedPlayer().ifPresentOrElse(
                                    player -> {
                                        ConnectionContext context = player.getConnectionContext();
                                        var target = player.getProxy().getServerManager().getServer(serverName);
                                        if (target == null) {
                                            player.sendMessage(
                                                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                                                            context.getTranslation("starlight.command.server.server_not_found"),
                                                            Placeholder.parsed("server_name", serverName)
                                                    )
                                            );
                                        } else {
                                            if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().equals(target)) {
                                                player.sendMessage(
                                                        MiniMessageUtils.MINI_MESSAGE.deserialize(
                                                                context.getTranslation("starlight.command.server.already_on_target_server"),
                                                                Placeholder.parsed("server_name", serverName)
                                                        )
                                                );
                                                return;
                                            }
                                            player.sendMessage(
                                                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                                                            context.getTranslation("starlight.command.server.connecting"),
                                                            Placeholder.parsed("server_name", serverName)
                                                    )
                                            );
                                            player.connect(target);
                                        }
                                    },
                                    () -> ctx.getSource().sendMessage(
                                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                                    proxy.getTranslateManager().translate("starlight.command.only_player_can_use_command")
                                            )
                                    )
                            );
                            return 1;
                        })
                );
    }
}
