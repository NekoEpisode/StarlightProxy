package io.slidermc.starlight.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用于加载JAR插件类文件的类加载器。每个JAR文件对应一个实例。
 *
 * <p><b>类加载采用 parent-first 委托策略</b>，确保代理核心类优先使用代理自身的版本。
 * 插件必须与代理共用同一份 API 类型（如 {@code IPlugin}、{@code Component}），
 * 否则会出现两份不兼容的 Class。
 *
 * <p><b>资源加载采用 child-first 策略</b>，与类加载相反。原因：
 * {@link URLClassLoader} 默认让父加载器优先命中资源，而代理的 jar 根目录含有
 * {@code config.yml} 这类插件生态通用的资源名，会把插件自己 jar 内的同名资源完全遮蔽，
 * 使插件无法读取自己的默认配置文件。资源不具备"类型必须统一"的约束，
 * 因此这里让插件自己的资源优先，父加载器的资源作为兜底。
 *
 * <p>该策略同时保证插件之间互不可见：各插件的类加载器是兄弟关系，
 * 资源委派链只有"自身 → 代理"，不存在跨插件的查找路径。
 */
final class PluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /** JAR文件路径，用于错误信息展示。 */
    private final String jarPath;

    PluginClassLoader(URL jarUrl, ClassLoader parent, String jarPath) {
        super(new URL[]{jarUrl}, parent);
        this.jarPath = jarPath;
    }

    String getJarPath() {
        return jarPath;
    }

    /**
     * 资源查找：优先返回插件自身 jar 内的资源，找不到时才回退到父加载器。
     *
     * <p>注意这里使用 {@link #findResource(String)} 而非 {@code super.getResource(name)}：
     * 前者只查询本加载器登记的 URL，不会触发父委派。
     *
     * <p><b>该回退是给插件自己读资源用的</b>（例如 {@code getClass().getClassLoader()
     * .getResourceAsStream(...)}）。若需要"只读本插件 jar、绝不落到代理资源"的语义，
     * 请使用 {@link #findResource(String)} 或 {@code PluginManager#getPluginResource}。
     */
    @Override
    public URL getResource(String name) {
        URL own = findResource(name);
        if (own != null) {
            return own;
        }
        return super.getResource(name);
    }

    /**
     * 批量资源查找：插件自身的资源排在父加载器的资源之前，且不重复。
     *
     * <p>不能沿用 {@code super.getResources(name)}：{@link URLClassLoader} 的实现是
     * {@code parent.getResources(name)} 之后再追加本加载器 {@code ucp} 中的资源，
     * 而本加载器自身的 URL 就在 {@code ucp} 里。若先取 {@link #findResources(String)}
     * 再叠加 {@code super.getResources(name)}，插件 jar 中的每个资源都会出现两次，
     * 破坏资源扫描器、ServiceLoader 等按枚举逐个处理的使用方。
     *
     * <p>因此这里直接向 {@code parent} 取父级资源，并用 {@link LinkedHashSet} 去重：
     * 父级资源仍然全部保留（{@code META-INF/services} 等跨 jar 合并语义不变），
     * 顺序为"插件优先"，且插件自身的 URL 只出现一次。
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> merged = new LinkedHashSet<>();

        Enumeration<URL> own = findResources(name);
        while (own.hasMoreElements()) {
            merged.add(own.nextElement());
        }

        // 直接向父加载器请求，避免 super.getResources 再次把本插件的 URL 追加进来
        ClassLoader parent = getParent();
        Enumeration<URL> fromParent = parent != null
                ? parent.getResources(name)
                : getSystemClassLoader().getResources(name);
        while (fromParent.hasMoreElements()) {
            merged.add(fromParent.nextElement());
        }

        return Collections.enumeration(merged);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
