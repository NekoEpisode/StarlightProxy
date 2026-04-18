package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ServerCommand extends StarlightCommand {
    public ServerCommand() {
        super("server", "切换到指定服务器", "/server <名称>");
    }

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
            .executes(ctx -> {
                ctx.getSource().sendMessage(Component.text("用法: /server <名称>", NamedTextColor.RED));
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
                            var target = player.getProxy().getServerManager().getServer(serverName);
                            if (target == null) {
                                player.sendMessage(Component.text("未找到服务器: " + serverName, NamedTextColor.RED));
                            } else {
                                player.sendMessage(Component.text("正在连接到 " + serverName + "...", NamedTextColor.GREEN));
                                player.connect(target);
                            }
                        },
                        () -> ctx.getSource().sendMessage(Component.text("只有玩家才能使用此命令", NamedTextColor.RED))
                    );
                    return 1;
                })
            );
    }
}
