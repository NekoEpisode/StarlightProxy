package io.slidermc.starlight.api.profile;

import java.util.List;
import java.util.UUID;

public record GameProfile(String username, UUID uuid, List<Property> properties) {
    public record Property(String name, String value, String signature) {}
}
