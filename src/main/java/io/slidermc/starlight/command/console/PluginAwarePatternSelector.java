package io.slidermc.starlight.command.console;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.layout.PatternSelector;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.apache.logging.log4j.core.pattern.PatternParser;

import java.util.List;

/**
 * 按 logger 名在两种日志格式之间选择。
 *
 * <p>控制台接管 log4j2 输出时会用一个 appender 替换掉配置里所有的 console appender。
 * 若只给它一个格式，插件日志就会与代理自身日志长得完全一样、无法区分。
 * 本选择器把"插件日志带 {@code [插件名]}、其余不带"这一分流规则带进那个统一的 appender。
 *
 * <p>插件 logger 由 {@code PluginBase} 以 {@code plugin.<插件id>} 命名。
 */
public final class PluginAwarePatternSelector implements PatternSelector {

    /** 插件 logger 的名字前缀，与 {@code PluginBase} 的命名保持一致。 */
    private static final String PLUGIN_LOGGER_PREFIX = "plugin.";

    private final PatternFormatter[] pluginFormatters;
    private final PatternFormatter[] defaultFormatters;

    /**
     * @param configuration  log4j2 配置，用于解析 pattern
     * @param pluginPattern  插件日志使用的 pattern，应包含 {@code %logger{1}}
     * @param defaultPattern 其余日志使用的 pattern
     */
    public PluginAwarePatternSelector(final Configuration configuration,
                                      final String pluginPattern,
                                      final String defaultPattern) {
        PatternParser parser = PatternLayout.createPatternParser(configuration);
        this.pluginFormatters = parser.parse(pluginPattern).toArray(new PatternFormatter[0]);
        this.defaultFormatters = parser.parse(defaultPattern).toArray(new PatternFormatter[0]);
    }

    @Override
    public PatternFormatter[] getFormatters(final LogEvent event) {
        String loggerName = event.getLoggerName();
        if (loggerName != null && loggerName.startsWith(PLUGIN_LOGGER_PREFIX)) {
            return pluginFormatters;
        }
        return defaultFormatters;
    }

    /**
     * 插件日志使用的 formatter，便于测试断言两种格式确实不同。
     *
     * @return 插件格式的 formatter 列表
     */
    List<PatternFormatter> pluginFormatters() {
        return List.of(pluginFormatters);
    }

    /**
     * 非插件日志使用的 formatter。
     *
     * @return 默认格式的 formatter 列表
     */
    List<PatternFormatter> defaultFormatters() {
        return List.of(defaultFormatters);
    }
}
