package io.slidermc.starlight.network.context;

import io.slidermc.starlight.network.client.StarlightMinecraftClient;

public class DownstreamConnectionContext {
    private StarlightMinecraftClient client;

    public StarlightMinecraftClient getClient() {
        return client;
    }

    public void setClient(StarlightMinecraftClient client) {
        this.client = client;
    }
}
