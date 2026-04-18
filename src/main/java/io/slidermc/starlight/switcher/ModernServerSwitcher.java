package io.slidermc.starlight.switcher;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundStartConfigurationPacket;
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
                        newClient.getChannel().config().setAutoRead(true);
                        player.setCurrentServer(target);
                        log.debug("Switched {} to {}", player.getGameProfile().username(), target.getName());
                    });
                })
                .exceptionally(e -> {
                    log.error("Server switch to {} failed for {}: {}",
                            target.getName(), player.getGameProfile().username(), e.getMessage());
                    newClient.disconnect();
                    // Player stays on current server, no disruption.
                    return null;
                });
    }
}
