package io.slidermc.starlight.switcher;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.client.LoginResult;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundStartConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.configuration.ServerboundClientInformationConfigurationPacket;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

        try {
            return newClient.connectAsync()
                    .thenCompose(_ -> {
                        newClient.setPlayerChannel(player.getChannel());
                        return newClient.login(
                                ctx.getHandshakeInformation().getProtocolVersion(),
                                player.getGameProfile().username(),
                                player.getGameProfile().uuid()
                        );
                    })
                    .thenCompose(result -> switch (result) {
                    case LoginResult.Success() -> {
                        // Login succeeded — continue with reconfiguration protocol.
                        // Pause new downstream so its packets don't flow until upstream is also in CONFIGURATION.
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
                        ackFuture.orTimeout(10, TimeUnit.SECONDS);
                        player.getChannel().writeAndFlush(new ClientboundStartConfigurationPacket());

                        yield ackFuture.thenRun(() -> {
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
                            player.setPreviousServer(player.getCurrentServer().orElse(null));
                            player.setCurrentServer(target);
                            log.debug("Switched {} to {}", player.getGameProfile().username(), target.getName());
                        }).exceptionally(e -> {
                            log.warn("Reconfiguration timed out for {}", player.getGameProfile().username());
                            ctx.setPendingReconfiguration(null);
                            newClient.disconnect();
                            player.kick(MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    ctx.getTranslation("starlight.disconnect.reconfiguration_timeout")));
                            return null;
                        });
                    }
                    case LoginResult.Kicked(Component reason) -> {
                        log.warn(proxy.getTranslateManager().translate("starlight.logging.error.server_switch_failed"),
                                target.getName(), player.getGameProfile().username(),
                                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(reason));
                        player.sendMessage(
                                MiniMessageUtils.MINI_MESSAGE.deserialize(
                                        ctx.getTranslation("starlight.switching.error.failed_connect_to"),
                                        Placeholder.component("target", Component.text(target.getName())),
                                        Placeholder.component("error", reason)
                                )
                        );
                        newClient.disconnect();
                        yield CompletableFuture.<Void>completedFuture(null);
                    }
                    case LoginResult.Error(Throwable cause) -> {
                        log.error(proxy.getTranslateManager().translate("starlight.logging.error.server_switch_failed"),
                                target.getName(), player.getGameProfile().username(), cause.getMessage());
                        String errorMsg = cause.getMessage() != null ? cause.getMessage()
                                : ctx.getTranslation("starlight.unknown_error");
                        player.sendMessage(
                                MiniMessageUtils.MINI_MESSAGE.deserialize(
                                        ctx.getTranslation("starlight.switching.error.error_on_connecting"),
                                        Placeholder.parsed("target", target.getName()),
                                        Placeholder.parsed("error", errorMsg)
                                )
                        );
                        newClient.disconnect();
                        yield CompletableFuture.<Void>completedFuture(null);
                    }
                })
                .exceptionally(e -> {
                    // Connection failure (connectAsync stage failed).
                    log.error(proxy.getTranslateManager().translate("starlight.logging.error.server_switch_failed"),
                            target.getName(), player.getGameProfile().username(),
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    newClient.disconnect();
                    ConnectionContext context = player.getConnectionContext();
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    if (errorMsg == null) errorMsg = context.getTranslation("starlight.unknown_error");
                    player.sendMessage(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    context.getTranslation("starlight.switching.error.error_on_connecting"),
                                    Placeholder.parsed("target", target.getName()),
                                    Placeholder.parsed("error", errorMsg)
                            )
                    );
                    return null;
                });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
