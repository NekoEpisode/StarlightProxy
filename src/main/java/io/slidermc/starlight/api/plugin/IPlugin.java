package io.slidermc.starlight.api.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;

/**
 * 所有插件（无论来自JAR还是内存注册）的统一接口。
 *
 * <p>生命周期顺序：
 * <ol>
 *   <li>{@link #onLoad(TranslateManager)} — I18N加载之后，代理启动之前调用，可在此注册插件翻译键和额外的内存插件</li>
 *   <li>{@link #onEnable(StarlightProxy)} — 代理完全启动后调用</li>
 *   <li>{@link #onReload(StarlightProxy)} — 收到重载指令时调用</li>
 *   <li>{@link #onDisable()} — 代理关闭时调用，应释放所有资源</li>
 * </ol>
 */
public interface IPlugin {

    /**
     * 在I18N系统加载之后、代理启动之前调用。
     * 可在此通过 {@link TranslateManager#addTranslation(String, String, String)} 注册插件自己的翻译键，
     * 也可在此向 {@code PluginManager} 注册额外的内存插件。
     * 默认实现为空操作。
     *
     * @param translateManager 翻译管理器
     */
    default void onLoad(TranslateManager translateManager) {}

    /**
     * 在代理完全启动后调用。
     *
     * @param proxy 已启动的代理实例
     */
    void onEnable(StarlightProxy proxy);

    /**
     * 在代理优雅关闭时调用。应释放所有资源、取消订阅事件。
     * 默认实现为空操作。
     */
    default void onDisable() {}

    /**
     * 在插件收到重载指令时调用。
     * 默认实现为先禁用再启用。
     *
     * @param proxy 代理实例
     */
    default void onReload(StarlightProxy proxy) {
        onDisable();
        onEnable(proxy);
    }

    /**
     * 返回插件元数据描述。
     */
    PluginDescription getDescription();

    /**
     * 返回插件专属日志记录器，名称为 {@code plugin.<插件名>}。
     */
    Logger getLogger();
}
