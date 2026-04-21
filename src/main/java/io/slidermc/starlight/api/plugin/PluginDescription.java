package io.slidermc.starlight.api.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 描述插件的元数据，对应 {@code plugin.yml} 中的字段。
 *
 * <p>{@code plugin.yml} 示例：
 * <pre>{@code
 * name: MyPlugin
 * version: 1.0.0
 * main: com.example.MyPlugin
 * api-version: 1.0
 * description: 一个示例插件
 * authors:
 *   - ExampleAuthor
 * depends:
 *   - OtherPlugin
 * soft-depends:
 *   - OptionalPlugin
 * }</pre>
 *
 * @param name        插件唯一名称（必填）
 * @param version     插件版本（必填）
 * @param main        主类全限定名，必须实现 {@link IPlugin}（必填）
 * @param apiVersion  目标API版本，当前仅作保留字段（可选）
 * @param description 插件简介（可选）
 * @param authors     作者列表（可选）
 * @param depends     硬依赖插件名列表，缺失时拒绝加载（可选）
 * @param softDepends 软依赖插件名列表，加载失败时仍继续（可选）
 */
public record PluginDescription(
        String name,
        String version,
        String main,
        String apiVersion,
        String description,
        List<String> authors,
        List<String> depends,
        List<String> softDepends
) {

    /**
     * 为内存插件创建最简描述，无依赖、无附加元数据。
     *
     * @param name    插件唯一名称
     * @param version 插件版本
     * @return 描述对象
     */
    public static PluginDescription memory(String name, String version) {
        return new PluginDescription(name, version, name, null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 从 snakeyaml 解析得到的原始 Map 构建 {@link PluginDescription} 的工厂方法。
     *
     * @param map YAML 顶层 Map
     * @return 解析后的描述对象
     * @throws PluginLoadException 若必填字段缺失
     */
    public static PluginDescription fromMap(Map<String, Object> map) throws PluginLoadException {
        String name = requireString(map, "name");
        String version = requireString(map, "version");
        String main = requireString(map, "main");
        String apiVersion = map.containsKey("api-version") && map.get("api-version") != null
                ? map.get("api-version").toString()
                : null;
        String description = map.containsKey("description") && map.get("description") != null
                ? map.get("description").toString()
                : null;
        List<String> authors = toStringList(map.get("authors"));
        List<String> depends = toStringList(map.get("depends"));
        List<String> softDepends = toStringList(map.get("soft-depends"));
        return new PluginDescription(name, version, main, apiVersion, description, authors, depends, softDepends);
    }

    private static String requireString(Map<String, Object> map, String key) throws PluginLoadException {
        Object value = map.get(key);
        if (value == null) {
            throw new PluginLoadException("plugin.yml 缺少必填字段: " + key);
        }
        String s = value.toString().strip();
        if (s.isBlank()) {
            throw new PluginLoadException("plugin.yml 缺少必填字段: " + key);
        }
        return s;
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof String)
                    .map(o -> (String) o)
                    .toList();
        }
        return Collections.emptyList();
    }
}

