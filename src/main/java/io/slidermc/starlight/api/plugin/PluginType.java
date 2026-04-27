package io.slidermc.starlight.api.plugin;

public enum PluginType {

    MEMORY("starlight.plugin.type.memory"),
    JAR("starlight.plugin.type.jar");

    private final String displayKey;

    PluginType(String displayKey) {
        this.displayKey = displayKey;
    }

    public String displayKey() {
        return displayKey;
    }
}
