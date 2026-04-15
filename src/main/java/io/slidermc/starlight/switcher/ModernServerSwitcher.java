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

        CompletableFuture<Void> ackFuture = new CompletableFuture<>();
        ctx.setPendingReconfiguration(ackFuture);
        player.getChannel().writeAndFlush(new ClientboundStartConfigurationPacket());

        return ackFuture
                .thenCompose(_ -> {
                    // Upstream is now in CONFIGURATION state.
                    // Buffer upstream packets until the new downstream is ready.
                    player.getChannel().config().setAutoRead(false);

                    Channel oldDownstream = ctx.getDownstreamChannel();
                    ctx.setDownstreamChannel(null);
                    if (oldDownstream != null) {
                        oldDownstream.close();
                    }

                    StarlightMinecraftClient newClient = new StarlightMinecraftClient(
                            target.getAddress(),
                            proxy.getRegistryPacketUtils().getPacketRegistry(),
                            proxy
                    );

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
                                .thenRun(() -> {
                                    ctx.setDownstreamChannel(newClient.getChannel());
                                    player.getChannel().config().setAutoRead(true);
                                    log.debug("Switched {} to {}", player.getGameProfile().username(), target.getName());
                                });
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                })
                .exceptionally(e -> {
                    log.error("Server switch failed for {}", player.getGameProfile().username(), e);
                    player.getChannel().config().setAutoRead(true);
                    player.getChannel().close();
                    return null;
                });
    }
}
