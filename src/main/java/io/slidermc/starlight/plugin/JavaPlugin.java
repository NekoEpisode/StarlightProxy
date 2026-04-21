package io.slidermc.starlight.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.plugin.IPlugin;
import io.slidermc.starlight.api.plugin.PluginDescription;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAR插件的抽象基类。
 *
 * <p>插件开发者应继承此类，并在 {@code plugin.yml} 的 {@code main} 字段填写子类的全限定名。
 * 该类提供了对 {@link PluginManager} 的访问能力，允许在 {@link #onLoad(TranslateManager)} 阶段注册额外的内存插件。
 *
 * <p>子类不应自行提供无参构造器以外的构造器，插件实例由加载器通过无参构造器反射创建。
 */
public abstract class JavaPlugin implements IPlugin {

    private PluginDescription description;
    private Logger logger;
    private PluginManager pluginManager;

    /**
     * 由 {@link PluginManager} 在实例化后立即调用，完成必要字段注入。
     * 不对外暴露。
     */
    final void init(PluginDescription description, PluginManager pluginManager) {
        this.description = description;
        this.logger = LoggerFactory.getLogger("plugin." + description.name());
        this.pluginManager = pluginManager;
    }

    @Override
    public final PluginDescription getDescription() {
        return description;
    }

    @Override
    public final Logger getLogger() {
        return logger;
    }

    /**
     * 返回插件管理器实例，可在 {@link #onLoad(TranslateManager)} 中用于注册内存插件。
     */
    protected final PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public void onLoad(TranslateManager translateManager) {}

    @Override
    public void onEnable(StarlightProxy proxy) {}

    @Override
    public void onDisable() {}

    @Override
    public void onReload(StarlightProxy proxy) {
        onDisable();
        onEnable(proxy);
    }
}
