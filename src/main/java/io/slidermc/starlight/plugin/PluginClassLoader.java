package io.slidermc.starlight.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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
     * 批量资源查找：插件自身的资源排在父加载器的资源之前。
     *
     * <p>父加载器的资源仍然全部包含在内，因此 {@code META-INF/services} 等
     * 需要跨 jar 合并的资源语义不变，只是顺序变为插件优先。
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> merged = new ArrayList<>();

        Enumeration<URL> own = findResources(name);
        while (own.hasMoreElements()) {
            merged.add(own.nextElement());
        }

        Enumeration<URL> fromParent = super.getResources(name);
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
