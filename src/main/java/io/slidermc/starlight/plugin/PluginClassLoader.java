package io.slidermc.starlight.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 用于加载JAR插件类文件的类加载器。每个JAR文件对应一个实例。
 *
 * <p>采用 parent-first 委托策略，确保代理核心类优先使用代理自身的版本。
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

    @Override
    public void close() throws IOException {
        super.close();
    }
}

