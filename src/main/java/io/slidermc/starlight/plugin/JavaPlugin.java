package io.slidermc.starlight.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.CommandMeta;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.event.EventListener;
import io.slidermc.starlight.api.plugin.IPlugin;
import io.slidermc.starlight.api.plugin.PluginDescription;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

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
    private File dataFolder;
    protected StarlightProxy proxy;

    private final AtomicLong currentId = new AtomicLong(0);

    /**
     * 由 {@link PluginManager} 在实例化后立即调用，完成必要字段注入。
     * 不对外暴露。
     */
    final void init(PluginDescription description, PluginManager pluginManager) {
        this.description = description;
        this.logger = LoggerFactory.getLogger("plugin." + description.id());
        this.pluginManager = pluginManager;
    }

    /**
     * 由 {@link PluginManager} 在加载时调用，记录数据文件夹路径但不立即创建。
     */
    final void initDataFolder(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * 获取此插件的数据文件夹（{@code plugins/<plugin-id>/}），首次调用时自动创建目录。
     *
     * @return 插件专属的数据文件夹，仅 JAR 插件有效
     */
    public final File getDataFolder() {
        if (!dataFolder.exists()) {
            try {
                Files.createDirectories(dataFolder.toPath());
            } catch (IOException e) {
                logger.error("Failed to create data folder: {}", dataFolder, e);
            }
        }
        return dataFolder;
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
    public final void onEnable(StarlightProxy proxy) {
        this.proxy = proxy;
        onEnable$0();
    }

    /**
     * 请实现此方法而非onEnable
     * 使用proxy字段来获得反代实例
     */
    public void onEnable$0() {}

    @Override
    public void onDisable() {}

    @Override
    public void onReload(StarlightProxy proxy) {
        onDisable();
        onEnable(proxy);
    }

    @Override
    public void registerListener(String listenerId, io.slidermc.starlight.api.event.EventListener listener) {
        if (proxy == null) throw new IllegalStateException("proxy field is null");
        proxy.getEventManager().register(this, listenerId, listener);
    }

    @Override
    public void registerListener(EventListener listener) {
        registerListener("listener-" + currentId.getAndIncrement(), listener);
    }

    @Override
    public void unregisterListener(String listenerId) {
        if (proxy == null) throw new IllegalStateException("proxy field is null");
        proxy.getEventManager().unregister(this, listenerId);
    }

    @Override
    public void registerCommand(StarlightCommand command) {
        if (proxy == null) throw new IllegalStateException("proxy field is null");
        proxy.getCommandManager().register(command);
    }

    /** 创建预填本插件 namespace 的 CommandMeta.Builder。 */
    protected CommandMeta.Builder commandBuilder(String name) {
        return CommandMeta.builder(getDescription().id(), name);
    }
}
