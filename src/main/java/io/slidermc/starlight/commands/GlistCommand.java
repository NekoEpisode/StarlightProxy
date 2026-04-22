package io.slidermc.starlight.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.player.PlayerManager;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.manager.ServerManager;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /glist} 命令，查询代理上的在线玩家分布。
 *
 * <ul>
 *   <li>{@code /glist} — 显示全局在线人数与服务器数量</li>
 *   <li>{@code /glist all} — 遍历所有服务器，各自显示玩家列表</li>
 *   <li>{@code /glist <server>} — 显示指定服务器的玩家列表</li>
 * </ul>
 */
public class GlistCommand extends StarlightCommand {

    private final StarlightProxy proxy;

    public GlistCommand(StarlightProxy proxy) {
        super("glist", "Show online players across all servers", "/glist help");
        this.proxy = proxy;
    }

    @Override
    public LiteralArgumentBuilder<IStarlightCommandSource> build() {
        return literal(getName())
                .executes(ctx -> {
                    sendSummary(ctx.getSource());
                    return 1;
                })
                .then(literal("all")
                        .executes(ctx -> {
                            sendAll(ctx.getSource(), false);
                            return 1;
                        })
                        .then(literal("force")
                                .executes(ctx -> {
                                    sendAll(ctx.getSource(), true);
                                    return 1;
                                })))
                .then(literal("help")
                        .executes(ctx -> {
                            sendHelp(ctx.getSource());
                            return 1;
                        }))
                .then(RequiredArgumentBuilder.<IStarlightCommandSource, String>argument("server", StringArgumentType.word())
                        .suggests((_, builder) -> {
                            proxy.getServerManager().getServers()
                                    .forEach(s -> builder.suggest(s.getName()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "server");
                            sendServer(ctx.getSource(), name);
                            return 1;
                        }));
    }

    private void sendSummary(IStarlightCommandSource src) {
        int players = proxy.getPlayerManager().getPlayers().size();
        int servers = proxy.getServerManager().getServers().size();
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.summary"),
                Placeholder.parsed("players", String.valueOf(players)),
                Placeholder.parsed("servers", String.valueOf(servers))));
    }

    private void sendAll(IStarlightCommandSource src, boolean force) {
        ServerManager sm = proxy.getServerManager();
        PlayerManager pm = proxy.getPlayerManager();
        List<ProxiedServer> servers = sm.getServers();

        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.all.header"),
                Placeholder.parsed("servers", String.valueOf(servers.size()))));

        int shown = 0;
        for (ProxiedServer server : servers) {
            List<ProxiedPlayer> players = pm.getPlayers(server);
            if (!force && players.isEmpty()) continue;
            sendServerEntry(src, server, players);
            shown++;
        }

        if (shown == 0) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.glist.all.empty")));
        }

        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.all.footer"),
                Placeholder.parsed("players", String.valueOf(pm.getPlayers().size())),
                Placeholder.parsed("servers", String.valueOf(servers.size()))));
    }

    private void sendServer(IStarlightCommandSource src, String serverName) {
        ProxiedServer server = proxy.getServerManager().getServer(serverName);
        if (server == null) {
            src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    t(src, "starlight.command.glist.server_not_found"),
                    Placeholder.parsed("server", serverName)));
            return;
        }
        List<ProxiedPlayer> players = proxy.getPlayerManager().getPlayers(server);
        sendServerEntry(src, server, players);
    }

    private void sendServerEntry(IStarlightCommandSource src, ProxiedServer server, List<ProxiedPlayer> players) {
        String names = players.isEmpty()
                ? t(src, "starlight.command.glist.no_players")
                : players.stream()
                        .map(p -> p.getGameProfile().username())
                        .collect(Collectors.joining("<dark_gray>, </dark_gray>"));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.server_entry"),
                Placeholder.parsed("server", server.getName()),
                Placeholder.parsed("count", String.valueOf(players.size())),
                Placeholder.parsed("players", names)));
    }

    private void sendHelp(IStarlightCommandSource src) {
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.help.header")));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.help.summary")));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.help.all")));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.help.all_force")));
        src.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                t(src, "starlight.command.glist.help.server")));
    }

    /**
     * 翻译辅助：玩家用客户端 locale，非玩家用代理默认 locale。
     */
    private String t(IStarlightCommandSource src, String key) {
        return src.asProxiedPlayer()
                .map(p -> p.getConnectionContext().getTranslation(key))
                .orElseGet(() -> proxy.getTranslateManager().translate(key));
    }
}

