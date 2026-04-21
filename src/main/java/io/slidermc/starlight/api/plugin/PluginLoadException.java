package io.slidermc.starlight.api.plugin;

/**
 * 插件加载过程中发生错误时抛出的异常。
 */
public class PluginLoadException extends Exception {

    public PluginLoadException(String message) {
        super(message);
    }

    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

