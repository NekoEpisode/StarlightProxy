package io.slidermc.starlight.plugin;

import io.slidermc.starlight.api.plugin.IPlugin;
import io.slidermc.starlight.api.plugin.PluginDescription;
import org.slf4j.Logger;

/**
 * 持有单个插件实例及其关联元数据的内部容器。
 * 仅在 {@code io.slidermc.starlight.plugin} 包内使用。
 */
final class PluginContainer {

    private final PluginDescription description;
    private final IPlugin plugin;
    private final Logger logger;
    private final PluginClassLoader classLoader;
    private boolean enabled = false;

    PluginContainer(PluginDescription description, IPlugin plugin, Logger logger, PluginClassLoader classLoader) {
        this.description = description;
        this.plugin = plugin;
        this.logger = logger;
        this.classLoader = classLoader;
    }

    PluginDescription description() { return description; }
    IPlugin plugin() { return plugin; }
    Logger logger() { return logger; }
    PluginClassLoader classLoader() { return classLoader; }
    boolean isEnabled() { return enabled; }
    void setEnabled(boolean enabled) { this.enabled = enabled; }
}

