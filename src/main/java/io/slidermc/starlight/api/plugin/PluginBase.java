package io.slidermc.starlight.api.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.event.EventListener;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存插件的抽象基类。
 *
 * <p>与 {@code JavaPlugin}（需要JAR + plugin.yml）不同，{@code PluginBase} 通过构造器直接
 * 接收 {@link PluginDescription}，无需任何外部文件，适用于以编程方式创建并注册插件的场景，例如：
 * <ul>
 *   <li>代理内部模块希望以插件形式参与生命周期管理</li>
 *   <li>JAR 插件希望在 {@link #onLoad(TranslateManager)} 阶段动态注册额外的功能单元</li>
 *   <li>单元测试或工具脚本中以编程方式注册的功能单元</li>
 * </ul>
 *
 * <p>使用示例（在某个 JAR 插件的 {@code onLoad()} 中）：
 * <pre>{@code
 * public class MyFeature extends PluginBase {
 *     public MyFeature() {
 *         super(PluginDescription.memory("MyFeature", "1.0.0"));
 *     }
 *
 *     @Override
 *     public void onEnable(StarlightProxy proxy) {
 *         getLogger().info("MyFeature 已启用");
 *     }
 * }
 *
 * // 在宿主 JAR 插件的 onLoad() 里：
 * getPluginManager().registerPlugin(new MyFeature());
 * }</pre>
 */

public abstract class PluginBase implements IPlugin {

    private final PluginDescription description;
    private final Logger logger;
    protected StarlightProxy proxy;

    private final AtomicLong currentId = new AtomicLong(0);

    /**
     * 通过描述构造内存插件，Logger 名称自动设为 {@code plugin.<插件名>}。
     *
     * @param description 插件元数据描述
     */
    protected PluginBase(PluginDescription description) {
        this.description = description;
        this.logger = LoggerFactory.getLogger("plugin." + description.name());
    }

    @Override
    public final PluginDescription getDescription() {
        return description;
    }

    @Override
    public final Logger getLogger() {
        return logger;
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
}

