package io.slidermc.starlight.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.plugin.IPlugin;
import io.slidermc.starlight.api.plugin.PluginDescription;
import io.slidermc.starlight.api.plugin.PluginLoadException;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 插件生命周期的中央管理器。
 *
 * <p>职责：
 * <ul>
 *   <li>从指定目录扫描并加载JAR插件</li>
 *   <li>解析 {@code plugin.yml}，构建依赖图并进行拓扑排序</li>
 *   <li>支持在 {@link IPlugin#onLoad(TranslateManager)} 阶段注册内存插件</li>
 *   <li>按顺序驱动所有生命周期方法</li>
 *   <li>优雅关闭时按逆序调用 {@link IPlugin#onDisable()}</li>
 * </ul>
 */
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final TranslateManager translateManager;

    /** 已排序的插件容器列表，顺序即为启用顺序。 */
    private final List<PluginContainer> orderedPlugins = new CopyOnWriteArrayList<>();

    /** 在加载阶段（loadPhase）期间通过 registerPlugin 注册的内存插件暂存队列。 */
    private final List<PluginContainer> pendingMemoryPlugins = new CopyOnWriteArrayList<>();

    /** 是否处于加载阶段（即正在执行 onLoad 回调期间）。 */
    private volatile boolean loadPhaseActive = false;

    /** 插件目录，在 loadPlugins 时设置。 */
    private volatile Path pluginsDir;

    /** 已启动的代理实例，enableAll 后设置。 */
    private volatile StarlightProxy proxy = null;

    /**
     * 构造插件管理器。
     *
     * @param translateManager 翻译管理器，用于所有非 debug 级别日志
     */
    public PluginManager(TranslateManager translateManager) {
        this.translateManager = translateManager;
    }

    /**
     * 扫描指定目录下的所有 {@code .jar} 文件，完成以下步骤：
     * <ol>
     *   <li>解析每个 JAR 中的 {@code plugin.yml}</li>
     *   <li>检查硬依赖是否存在</li>
     *   <li>通过拓扑排序确定加载顺序</li>
     *   <li>按顺序实例化插件并调用 {@link IPlugin#onLoad(TranslateManager)}</li>
     * </ol>
     *
     * <p>目录不存在时会自动创建，不抛出异常。
     *
     * @param directory 插件目录
     */
    public void loadPlugins(Path directory) {
        this.pluginsDir = directory;
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                log.error(t("starlight.logging.error.plugin.directory_create_failed"), directory, e);
            }
            return;
        }

        List<Path> jarFiles = collectJars(directory);
        if (jarFiles.isEmpty()) {
            return;
        }

        List<PluginContainer> discovered = new ArrayList<>();
        for (Path jar : jarFiles) {
            try {
                PluginContainer container = loadFromJar(jar);
                discovered.add(container);
            } catch (PluginLoadException e) {
                log.error(t("starlight.logging.error.plugin.jar_load_failed"), jar.getFileName(), e.getMessage(), e);
            }
        }

        List<PluginContainer> sorted = topologicalSort(discovered);

        loadPhaseActive = true;
        for (PluginContainer container : sorted) {
            orderedPlugins.add(container);
            invokeOnLoad(container);
            flushPendingMemoryPlugins();
        }
        loadPhaseActive = false;
    }

    /**
     * 注册一个内存插件。
     *
     * <p>插件描述直接取自 {@link IPlugin#getDescription()}，适合配合 {@code PluginBase} 使用。
     *
     * @param plugin 插件实例，必须已持有有效的 {@link io.slidermc.starlight.api.plugin.PluginDescription}
     * @see #registerPlugin(io.slidermc.starlight.api.plugin.PluginDescription, IPlugin)
     */
    public void registerPlugin(IPlugin plugin) {
        registerPlugin(plugin.getDescription(), plugin);
    }

    /**
     * 注册一个内存插件。
     *
     * <p>若在加载阶段（{@link #loadPlugins} 执行期间）调用，该插件将在当前插件的
     * {@code onLoad(TranslateManager)} 返回后立即收到 {@code onLoad(TranslateManager)} 回调；
     * 若在加载阶段结束后调用，{@code onLoad()} 将立即执行。
     *
     * <p>若代理已启动（{@link #enableAll(StarlightProxy)} 已调用），
     * 该插件将在注册后立即收到 {@link IPlugin#onEnable(StarlightProxy)} 回调。
     *
     * @param description 插件描述
     * @param plugin      插件实例
     */
    public void registerPlugin(PluginDescription description, IPlugin plugin) {
        if (orderedPlugins.stream().anyMatch(c -> c.description().id().equals(description.id()))
                || pendingMemoryPlugins.stream().anyMatch(c -> c.description().id().equals(description.id()))) {
            log.warn(t("starlight.logging.warn.plugin.duplicate"), description.id());
            return;
        }

        PluginContainer container = new PluginContainer(description, plugin, null);

        if (plugin instanceof io.slidermc.starlight.api.plugin.PluginBase pb) {
            pb.setPluginManager(this);
        }

        if (loadPhaseActive) {
            pendingMemoryPlugins.add(container);
        } else {
            orderedPlugins.add(container);
            invokeOnLoad(container);
            if (proxy != null) {
                invokeOnEnable(container, proxy);
            }
        }
    }

    /**
     * 按顺序调用所有已加载插件的 {@link IPlugin#onEnable(StarlightProxy)}。
     * 应在代理完全启动后调用。
     *
     * @param proxy 代理实例
     */
    public void enableAll(StarlightProxy proxy) {
        this.proxy = proxy;
        for (PluginContainer container : orderedPlugins) {
            if (!container.isEnabled()) {
                invokeOnEnable(container, proxy);
            }
        }
    }

    /**
     * 按启用逆序调用所有插件的 {@link IPlugin#onDisable()}，并释放类加载器资源。
     * 应在代理关闭时调用。
     * 注: disableAll后此实例不可复用，如需要，请创建新的PluginManager
     */
    public void disableAll() {
        List<PluginContainer> reversed = new ArrayList<>(orderedPlugins);
        Collections.reverse(reversed);
        for (PluginContainer container : reversed) {
            if (container.isEnabled()) {
                invokeOnDisable(container);
            }
        }
        for (PluginContainer container : orderedPlugins) {
            if (container.classLoader() != null) {
                try {
                    container.classLoader().close();
                } catch (IOException e) {
                    log.warn(t("starlight.logging.warn.plugin.classloader_close_failed"), container.description().id(), e);
                }
            }
        }
        orderedPlugins.clear();
    }

    /**
     * 按顺序对所有已启用插件调用 {@link IPlugin#onReload(StarlightProxy)}。
     *
     * @param proxy 代理实例
     */
    public void reloadAll(StarlightProxy proxy) {
        for (PluginContainer container : orderedPlugins) {
            if (container.isEnabled()) {
                try {
                    container.plugin().onReload(proxy);
                    log.info(t("starlight.logging.info.plugin.reloaded"), container.description().id());
                } catch (Exception e) {
                    log.error(t("starlight.logging.error.plugin.on_reload_failed"), container.description().id(), e);
                }
            }
        }
    }

    /**
     * 按 ID 查找已加载的插件实例。
     *
     * @param id 插件 ID（唯一标识符，非显示名称）
     * @return 插件实例，不存在时返回 {@link Optional#empty()}
     */
    public Optional<IPlugin> getPlugin(String id) {
        return orderedPlugins.stream()
                .filter(c -> c.description().id().equals(id))
                .map(PluginContainer::plugin)
                .findFirst();
    }

    /**
     * 查询指定 ID 的插件是否已启用。
     *
     * @param id 插件 ID（唯一标识符，非显示名称）
     * @return {@link Optional#empty()} 若插件不存在；否则返回启用状态
     */
    public Optional<Boolean> isPluginEnabled(String id) {
        return orderedPlugins.stream()
                .filter(c -> c.description().id().equals(id))
                .map(PluginContainer::isEnabled)
                .findFirst();
    }

    /**
     * 返回所有已加载插件描述的不可修改视图。
     */
    public List<PluginDescription> getLoadedPlugins() {
        return orderedPlugins.stream().map(PluginContainer::description).toList();
    }

    /**
     * 启用指定 ID 的插件（若已启用则无操作）。
     *
     * @param id 插件 ID（唯一标识符，非显示名称）
     * @return 若插件存在且未被启用返回 true；否则 false
     */
    public boolean enablePlugin(String id) {
        PluginContainer container = findContainer(id);
        if (container == null) {
            log.warn(t("starlight.logging.warn.plugin.not_found"), id);
            return false;
        }
        if (container.isEnabled()) {
            return false;
        }
        if (proxy == null) {
            log.warn(t("starlight.logging.warn.plugin.enable_without_proxy"), id);
            return false;
        }
        invokeOnEnable(container, proxy);
        return true;
    }

    /**
     * 禁用指定 ID 的插件（若未启用则无操作）。
     * 会同时清理该插件的命令和事件监听器。
     *
     * @param id 插件 ID（唯一标识符，非显示名称）
     * @return 若插件存在且处于启用状态返回 true；否则 false
     */
    public boolean disablePlugin(String id) {
        PluginContainer container = findContainer(id);
        if (container == null) {
            log.warn(t("starlight.logging.warn.plugin.not_found"), id);
            return false;
        }
        if (!container.isEnabled()) {
            return false;
        }
        invokeOnDisable(container);
        return true;
    }

    /**
     * 注销指定 ID 的插件。若其已启用会先调用 {@link #disablePlugin(String)}，
     * 随后移除所有内部状态（包括类加载器等资源）。
     *
     * @param id 插件 ID（唯一标识符，非显示名称）
     * @return 若插件存在返回 true；否则 false
     */
    public boolean unregisterPlugin(String id) {
        PluginContainer container = findContainer(id);
        if (container == null) {
            log.warn(t("starlight.logging.warn.plugin.not_found"), id);
            return false;
        }
        if (container.isEnabled()) {
            invokeOnDisable(container);
        }
        orderedPlugins.remove(container);
        if (container.classLoader() != null) {
            try {
                container.classLoader().close();
            } catch (IOException e) {
                log.warn(t("starlight.logging.warn.plugin.classloader_close_failed"), id, e);
            }
        }
        return true;
    }

    private PluginContainer findContainer(String id) {
        return orderedPlugins.stream()
                .filter(c -> c.description().id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private List<Path> collectJars(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(p -> p.toString().endsWith(".jar"))
                    .toList();
        } catch (IOException e) {
            log.error(t("starlight.logging.error.plugin.directory_list_failed"), directory, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从单个JAR文件读取 {@code plugin.yml}，创建类加载器并实例化主类。
     * 此时不调用任何生命周期方法。
     */
    private PluginContainer loadFromJar(Path jarPath) throws PluginLoadException {
        JarFile jarFile;
        try {
            jarFile = new JarFile(jarPath.toFile());
        } catch (IOException e) {
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.cannot_open_jar"), jarPath), e);
        }

        PluginDescription description;
        try {
            JarEntry entry = jarFile.getJarEntry("plugin.yml");
            if (entry == null) {
                jarFile.close();
                throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.plugin_yml_missing"), jarPath.getFileName()));
            }
            try (InputStream is = jarFile.getInputStream(entry)) {
                Yaml yaml = new Yaml();
                Map<String, Object> map = yaml.load(is);
                description = PluginDescription.fromMap(map);
            }
        } catch (PluginLoadException e) {
            try { jarFile.close(); } catch (IOException ignored) {}
            throw e;
        } catch (IOException e) {
            try { jarFile.close(); } catch (IOException ignored) {}
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.read_plugin_yml_failed"), jarPath.getFileName()), e);
        }

        PluginClassLoader classLoader;
        try {
            classLoader = new PluginClassLoader(
                    jarPath.toUri().toURL(),
                    getClass().getClassLoader(),
                    jarPath.toString()
            );
        } catch (Exception e) {
            try { jarFile.close(); } catch (IOException ignored) {}
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.classloader_create_failed"), jarPath.getFileName()), e);
        }

        Class<?> mainClass;
        try {
            mainClass = classLoader.loadClass(description.main());
        } catch (ClassNotFoundException e) {
            try { classLoader.close(); jarFile.close(); } catch (IOException ignored) {}
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.main_class_not_found"), description.main(), jarPath.getFileName()), e);
        }

        if (!IPlugin.class.isAssignableFrom(mainClass)) {
            try { classLoader.close(); jarFile.close(); } catch (IOException ignored) {}
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.main_class_not_plugin"), description.main()));
        }

        IPlugin instance;
        try {
            instance = (IPlugin) mainClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            try { classLoader.close(); jarFile.close(); } catch (IOException ignored) {}
            throw new PluginLoadException(formatTranslated(t("starlight.logging.error.plugin.instantiate_main_failed"), description.main()), e);
        }

        if (instance instanceof JavaPlugin jp) {
            jp.init(description, this);
            jp.initDataFolder(pluginsDir.resolve(description.id()).toFile());
        }

        log.info(t("starlight.logging.info.plugin.discovered"), description.id(), description.version());

        try {
            jarFile.close();
        } catch (IOException ignored) {}

        return new PluginContainer(description, instance, classLoader);
    }

    /**
     * 对已发现的插件列表执行拓扑排序（Kahn算法）。
     *
     * <p>处理策略：
     * <ul>
     *   <li>硬依赖（{@code depends}）缺失：跳过该插件，并传播跳过其所有（直接和传递性的）硬依赖方</li>
     *   <li>软依赖（{@code soft-depends}）缺失：仅打印警告，不影响加载</li>
     *   <li>循环依赖：跳过参与循环的所有插件，其余插件正常加载</li>
     * </ul>
     * 每种问题只影响有问题的插件本身及其依赖链，不会终止整批加载。
     */
    private List<PluginContainer> topologicalSort(List<PluginContainer> containers) {
        Map<String, PluginContainer> byName = new LinkedHashMap<>();
        for (PluginContainer c : containers) {
            byName.put(c.description().id(), c);
        }

        // 收集因硬依赖缺失而需要跳过的插件（传递性扩散，直到稳定）
        Set<String> excluded = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PluginContainer c : containers) {
                String name = c.description().id();
                if (excluded.contains(name)) continue;
                for (String dep : c.description().depends()) {
                    if (!byName.containsKey(dep) || excluded.contains(dep)) {
                        log.error(t("starlight.logging.error.plugin.jar_load_failed"),
                                name, t("starlight.logging.error.plugin.hard_dep_missing").replace("{dep}", dep));
                        excluded.add(name);
                        changed = true;
                        break;
                    }
                }
            }
        }

        // 仅对通过检查的插件构建依赖图
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (String name : byName.keySet()) {
            if (excluded.contains(name)) continue;
            inDegree.put(name, 0);
            dependents.put(name, new ArrayList<>());
        }

        for (PluginContainer c : containers) {
            String name = c.description().id();
            if (excluded.contains(name)) continue;
            for (String dep : c.description().depends()) {
                dependents.get(dep).add(name);
                inDegree.merge(name, 1, Integer::sum);
            }
            for (String softDep : c.description().softDepends()) {
                if (!byName.containsKey(softDep) || excluded.contains(softDep)) {
                    log.warn(t("starlight.logging.warn.plugin.soft_dep_missing"), name, softDep);
                    continue;
                }
                dependents.get(softDep).add(name);
                inDegree.merge(name, 1, Integer::sum);
            }
        }

        // Kahn 拓扑排序
        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((name, deg) -> {
            if (deg == 0) queue.offer(name);
        });

        List<PluginContainer> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byName.get(current));
            for (String dependent : dependents.getOrDefault(current, List.of())) {
                int newDeg = inDegree.merge(dependent, -1, Integer::sum);
                if (newDeg == 0) {
                    queue.offer(dependent);
                }
            }
        }

        // 排序后仍有剩余 = 循环依赖，单独跳过
        if (sorted.size() != inDegree.size()) {
            Set<String> inCycle = new HashSet<>(inDegree.keySet());
            sorted.forEach(c -> inCycle.remove(c.description().id()));
            log.error(t("starlight.logging.error.plugin.dependency_resolve_failed"),
                    t("starlight.logging.error.plugin.circular_dependency").replace("{plugins}", inCycle.toString()));
        }

        return sorted;
    }

    private void flushPendingMemoryPlugins() {
        if (pendingMemoryPlugins.isEmpty()) return;
        List<PluginContainer> toFlush = new ArrayList<>(pendingMemoryPlugins);
        pendingMemoryPlugins.clear();
        for (PluginContainer container : toFlush) {
            orderedPlugins.add(container);
            invokeOnLoad(container);
        }
    }

    private void invokeOnLoad(PluginContainer container) {
        try {
            container.plugin().onLoad(translateManager);
        } catch (Exception e) {
            log.error(t("starlight.logging.error.plugin.on_load_failed"), container.description().id(), e);
            invokeOnDisable(container);
            orderedPlugins.remove(container);
            if (container.classLoader() != null) {
                try {
                    container.classLoader().close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void invokeOnEnable(PluginContainer container, StarlightProxy proxy) {
        try {
            container.plugin().onEnable(proxy);
            container.setEnabled(true);
            log.info(t("starlight.logging.info.plugin.enabled"), container.description().id(), container.description().version());
        } catch (Exception e) {
            log.error(t("starlight.logging.error.plugin.on_enable_failed"), container.description().id(), e);
            invokeOnDisable(container);
        }
    }

    private void invokeOnDisable(PluginContainer container) {
        try {
            container.plugin().onDisable();
            log.info(t("starlight.logging.info.plugin.disabled"), container.description().id());
        } catch (Exception e) {
            log.error(t("starlight.logging.error.plugin.on_disable_failed"), container.description().id(), e);
        }
        container.setEnabled(false);
        if (proxy != null) {
            proxy.getEventManager().unregisterAll(container.plugin());
            proxy.getCommandManager().unregisterAll(container.description().id());
        }
    }

    /** 便捷方法，减少重复的 translateManager.translate() 调用。 */
    private String t(String key) {
        return translateManager.translate(key);
    }

    /** 简单的占位符替换，按顺序用 args 替换 pattern 中的 {}。返回格式化后的字符串。 */
    private String formatTranslated(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0) return pattern;
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < pattern.length()) {
            int j = pattern.indexOf("{}", i);
            if (j == -1) {
                sb.append(pattern, i, pattern.length());
                break;
            }
            sb.append(pattern, i, j);
            sb.append(args[argIndex] == null ? "null" : args[argIndex].toString());
            argIndex = Math.min(argIndex + 1, args.length - 1);
            i = j + 2;
        }
        return sb.toString();
    }
}

