package io.slidermc.starlight.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 用于加载JAR插件类文件的类加载器。每个JAR文件对应一个实例。
 *
 * <p><b>类加载采用 parent-first 委托策略</b>，确保代理核心类优先使用代理自身的版本。
 * 插件必须与代理共用同一份 API 类型（如 {@code IPlugin}、{@code Component}），
 * 否则会出现两份不兼容的 Class。
 *
 * <p><b>插件之间可以互相看到对方的类</b>：本加载器在自己与父加载器都找不到某个类时，
 * 会依次到其它已加载插件的 JAR 里查找。这样插件可以把另一个插件当作依赖使用
 * （例如 FastLogin 需要 Floodgate 的 API 类型），而不必各自打包一份同名类——
 * 各打一份会导致 {@code static} 单例状态分裂成两份。
 *
 * <p>查找顺序为：<b>父加载器 → 本插件 JAR → 其它插件 JAR</b>。父加载器优先保证代理 API 唯一；
 * 本插件 JAR 优先于其它插件，保证插件无法被同名类意外遮蔽。
 *
 * <p><b>资源加载采用 child-first 策略</b>，与类加载相反。原因：
 * {@link URLClassLoader} 默认让父加载器优先命中资源，而代理的 jar 根目录含有
 * {@code config.yml} 这类插件生态通用的资源名，会把插件自己 jar 内的同名资源完全遮蔽，
 * 使插件无法读取自己的默认配置文件。资源不具备"类型必须统一"的约束，
 * 因此这里让插件自己的资源优先，父加载器的资源作为兜底。
 *
 * <p>资源查找<b>不</b>跨插件：插件生态里同名资源极常见（{@code config.yml}、
 * {@code plugin.yml}），跨插件命中只会带来难以排查的错乱。
 */
final class PluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * 所有已加载插件的类加载器。
     *
     * <p>插件完成后可从注册表移除（{@link #close()}），此后不再参与兄弟查找。
     */
    private static final Set<PluginClassLoader> LOADERS = new CopyOnWriteArraySet<>();

    /** JAR文件路径，用于错误信息展示。 */
    private final String jarPath;

    PluginClassLoader(URL jarUrl, ClassLoader parent, String jarPath) {
        super(new URL[]{jarUrl}, parent);
        this.jarPath = jarPath;
    }

    /**
     * 把本加载器加入兄弟查找注册表。
     *
     * <p>必须在加载插件主类<b>之前</b>调用：否则插件在自身静态初始化阶段使用其它插件的类时，
     * 对方可能尚未进入注册表，查找会失败。
     */
    void addToClassLoaders() {
        LOADERS.add(this);
    }

    @Override
    public void close() throws IOException {
        LOADERS.remove(this);
        super.close();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return loadClass(name, resolve, true);
    }

    /**
     * 加载一个类，可选是否向其它插件查找。
     *
     * <p>{@code searchOtherPlugins} 在向兄弟加载器递归时必须为 {@code false}，否则
     * A 找不到的类会让 A 去找 B、B 再去找 A，在两个插件都没有该类时无限递归。
     *
     * @param name                类全名
     * @param resolve             是否解析
     * @param searchOtherPlugins  是否允许向其它插件查找
     * @return 加载到的类
     * @throws ClassNotFoundException 所有来源都找不到时抛出
     */
    Class<?> loadClass(String name, boolean resolve, boolean searchOtherPlugins)
            throws ClassNotFoundException {
        try {
            // 父加载器 → 本插件 JAR
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException ignored) {
            // 继续尝试其它插件
        }

        if (searchOtherPlugins) {
            for (PluginClassLoader other : LOADERS) {
                if (other == this) {
                    continue;
                }
                try {
                    return other.loadClass(name, resolve, false);
                } catch (ClassNotFoundException ignored) {
                    // 该插件没有这个类，继续找下一个
                }
            }
        }

        throw new ClassNotFoundException(name);
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
}
