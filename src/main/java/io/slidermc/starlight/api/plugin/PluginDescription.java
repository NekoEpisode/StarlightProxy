package io.slidermc.starlight.api.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 描述插件的元数据，对应 {@code plugin.yml} 中的字段。
 *
 * <p>{@code plugin.yml} 示例：
 * <pre>{@code
 * id: myplugin
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
 * @param id          插件唯一标识（必填，全小写）
 * @param name        插件显示名称（必填）
 * @param version     插件版本（必填）
 * @param main        主类全限定名，必须实现 {@link IPlugin}（必填）
 * @param apiVersion  目标API版本，当前仅作保留字段（可选）
 * @param description 插件简介（可选）
 * @param authors     作者列表（可选）
 * @param depends     硬依赖插件名列表，缺失时拒绝加载（可选）
 * @param softDepends 软依赖插件名列表，加载失败时仍继续（可选）
 * @param type        插件类型 {@link PluginType}（内存/JAR等）
 */
public record PluginDescription(
        String id,
        String name,
        String version,
        String main,
        String apiVersion,
        String description,
        List<String> authors,
        List<String> depends,
        List<String> softDepends,
        PluginType type
) {

    /**
     * 为内存插件创建最简描述，无依赖、无附加元数据。
     *
     * @param id      插件唯一标识（全小写）
     * @param version 插件版本
     * @return 描述对象，{@code name} 默认等于 {@code id}
     */
    public static PluginDescription memory(String id, String version) {
        return builder(id, version).build();
    }

    /**
     * 创建一个 Builder。
     *
     * @param id      插件唯一标识，必须全小写（必填）
     * @param version 插件版本（必填）
     * @return Builder 实例，{@code name} 默认与 {@code id} 相同，{@code main} 默认与 {@code id} 相同
     */
    public static Builder builder(String id, String version) {
        return new Builder(id, version);
    }

    public static final class Builder {
        private final String id;
        private final String version;
        private String name;
        private String main;
        private String apiVersion;
        private String description;
        private List<String> authors = Collections.emptyList();
        private List<String> depends = Collections.emptyList();
        private List<String> softDepends = Collections.emptyList();
        private PluginType type = PluginType.MEMORY;

        private Builder(String id, String version) {
            this.id = id;
            this.name = id;
            this.main = id;
            this.version = version;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder main(String main) { this.main = main; return this; }
        public Builder apiVersion(String apiVersion) { this.apiVersion = apiVersion; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder type(PluginType type) { this.type = type; return this; }

        public Builder authors(String... authors) { this.authors = List.of(authors); return this; }
        public Builder authors(List<String> authors) { this.authors = List.copyOf(authors); return this; }

        public Builder depends(String... depends) { this.depends = List.of(depends); return this; }
        public Builder depends(List<String> depends) { this.depends = List.copyOf(depends); return this; }

        public Builder softDepends(String... softDepends) { this.softDepends = List.of(softDepends); return this; }
        public Builder softDepends(List<String> softDepends) { this.softDepends = List.copyOf(softDepends); return this; }

        public PluginDescription build() {
            return new PluginDescription(id, name, version, main, apiVersion, description, authors, depends, softDepends, type);
        }
    }

    /**
     * 从 snakeyaml 解析得到的原始 Map 构建 {@link PluginDescription} 的工厂方法。
     *
     * @param map YAML 顶层 Map
     * @return 解析后的描述对象
     * @throws PluginLoadException 若必填字段缺失（id、version、main 三者均不可缺，name 缺失时等于 id）
     */
    public static PluginDescription fromMap(Map<String, Object> map) throws PluginLoadException {
        String id = requireString(map, "id").toLowerCase();
        String name = getOptionalString(map, "name");
        if (name == null) name = id;
        String version = requireString(map, "version");
        String main = requireString(map, "main");
        String apiVersion = getOptionalString(map, "api-version");
        String description = getOptionalString(map, "description");
        List<String> authors = toStringList(map.get("authors"));
        List<String> depends = toStringList(map.get("depends"));
        List<String> softDepends = toStringList(map.get("soft-depends"));
        return new PluginDescription(id, name, version, main, apiVersion, description, authors, depends, softDepends, PluginType.JAR);
    }

    private static String requireString(Map<String, Object> map, String key) throws PluginLoadException {
        Object value = map.get(key);
        if (value == null) {
            throw new PluginLoadException("plugin.yml 缺少必填字段: " + key);
        }
        String s = value.toString().strip();
        if (s.isBlank()) {
            throw new PluginLoadException("plugin.yml 字段为空: " + key);
        }
        return s;
    }

    private static String getOptionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        String s = value.toString().strip();
        return s.isBlank() ? null : s;
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
