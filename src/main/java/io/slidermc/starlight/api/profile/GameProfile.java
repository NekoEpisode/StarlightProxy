package io.slidermc.starlight.api.profile;

import java.util.List;
import java.util.UUID;

public record GameProfile(String username, UUID uuid, List<Property> properties) {
    public record Property(String name, String value, String signature) {}

    public GameProfile withUsername(String username) {
        return new GameProfile(username, this.uuid, this.properties);
    }

    public GameProfile withUuid(UUID uuid) {
        return new GameProfile(this.username, uuid, this.properties);
    }

    public GameProfile withProperties(List<Property> properties) {
        return new GameProfile(this.username, this.uuid, properties);
    }
}
