package io.slidermc.starlight.switcher;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundStartConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.configuration.ServerboundClientInformationConfigurationPacket;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ModernServerSwitcher {
    private static final Logger log = LoggerFactory.getLogger(ModernServerSwitcher.class);

    public static CompletableFuture<Void> switchServer(ProxiedPlayer player, ProxiedServer target, StarlightProxy proxy) {
        ConnectionContext ctx = player.getConnectionContext();

        StarlightMinecraftClient newClient = new StarlightMinecraftClient(
                target.getAddress(),
                proxy.getRegistryPacketUtils().getPacketRegistry(),
                proxy
        );
        // Suppress upstream player disconnect if new downstream rejects login.
        newClient.setSwitching(true);

        CompletableFuture<Void> loginFuture;
        try {
            loginFuture = newClient.connectAsync()
                    .thenCompose(_ -> {
                        newClient.setPlayerChannel(player.getChannel());
                        return newClient.login(
                                ctx.getHandshakeInformation().getProtocolVersion(),
                                player.getGameProfile().username(),
                                player.getGameProfile().uuid()
                        );
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        return loginFuture
                .thenCompose(_ -> {
                    // New downstream is now in CONFIGURATION state.
                    // Pause it so its packets don't flow until upstream is also in CONFIGURATION.
                    newClient.getChannel().config().setAutoRead(false);

                    // Disconnect old downstream. Upstream packets will be gracefully dropped
                    // by StarlightServerHandler until the new downstream is linked.
                    Channel oldDownstream = ctx.getDownstreamChannel();
                    ctx.setDownstreamChannel(null);
                    if (oldDownstream != null) {
                        oldDownstream.close();
                    }

                    // Notify upstream to enter CONFIGURATION state.
                    CompletableFuture<Void> ackFuture = new CompletableFuture<>();
                    ctx.setPendingReconfiguration(ackFuture);
                    player.getChannel().writeAndFlush(new ClientboundStartConfigurationPacket());

                    return ackFuture.thenRun(() -> {
                        // ServerboundConfigurationAckPacket.Listener has already set upstream states.
                        ctx.setDownstreamChannel(newClient.getChannel());

                        // 在 Configuration 阶段将客户端设置转发给新下游，
                        // 确保新服务器获得正确的语言、视距、皮肤等信息。
                        ctx.getClientInformation().ifPresentOrElse(
                                info -> newClient.getChannel().writeAndFlush(
                                        new ServerboundClientInformationConfigurationPacket(info)),
                                () -> log.warn("No ClientInformation available for {} during server switch to {}",
                                        player.getGameProfile().username(), target.getName())
                        );

                        newClient.getChannel().config().setAutoRead(true);
                        player.setCurrentServer(target);
                        log.debug("Switched {} to {}", player.getGameProfile().username(), target.getName());
                    });
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.error("Server switch to {} failed for {}: {}",
                            target.getName(), player.getGameProfile().username(), cause.getMessage());
                    newClient.disconnect();
                    // Notify the player about the failure instead of silently dropping the error.
                    if (cause instanceof ServerSwitchKickedException kicked) {
                        player.sendMessage(
                                Component.text("无法连接到 " + target.getName() + "：", NamedTextColor.RED)
                                        .append(kicked.getReason())
                        );
                    } else {
                        player.sendMessage(
                                Component.text("连接到 " + target.getName() + " 时出错：" + cause.getMessage(), NamedTextColor.RED)
                        );
                    }
                    return null;
                });
    }
}
