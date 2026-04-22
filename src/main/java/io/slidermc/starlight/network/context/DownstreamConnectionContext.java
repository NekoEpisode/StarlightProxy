package io.slidermc.starlight.network.context;

import io.netty.channel.Channel;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.packet.IMinecraftPacket;

import java.util.concurrent.CompletableFuture;

public class DownstreamConnectionContext {
    private StarlightMinecraftClient client;

    public StarlightMinecraftClient getClient() {
        return client;
    }

    public void setClient(StarlightMinecraftClient client) {
        this.client = client;
    }

    public CompletableFuture<Void> toUpstream(IMinecraftPacket packet) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (client != null) {
            Channel channel = client.getPlayerChannel();
            if (channel != null) {
                channel.writeAndFlush(packet).addListener(writeFuture -> {
                    if (writeFuture.isSuccess()) {
                        future.complete(null);
                    } else {
                        future.completeExceptionally(writeFuture.cause());
                    }
                });
            } else {
                future.completeExceptionally(new IllegalStateException("Player channel is null"));
            }
        } else {
            future.completeExceptionally(new IllegalStateException("Client is not set in DownstreamConnectionContext"));
        }
        return future;
    }
}
