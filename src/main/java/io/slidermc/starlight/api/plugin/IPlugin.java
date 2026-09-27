package io.slidermc.starlight.api.plugin;

import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.event.EventListener;
import io.slidermc.starlight.api.translate.TranslateManager;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;

import java.io.InputStream;

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
     * 向事件管理器注册一个监听器。
     * <p>等同于 proxy.getEventManager().register(this, listenerId, listener)，只能在 onEnable 或之后调用。
     * @param listenerId 监听器唯一 ID，同一插件内不可重复
     * @param listener   监听器实例
     */
    void registerListener(String listenerId, EventListener listener);

    /**
     * 注册一个随机/递增id(内部处理)的listener，用于后续不需要跟踪(unregister之类)的情况
     * @param listener 监听器实例
     */
    void registerListener(EventListener listener);

    /**
     * 注销本插件注册的指定 ID 监听器。
     * @param listenerId 监听器 ID
     */
    void unregisterListener(String listenerId);

    /**
     * 注册代理命令。需在 {@link #onEnable(StarlightProxy)} 或之后调用。
     */
    void registerCommand(StarlightCommand command);

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

    /**
     * 注册插件消息通道。
     *
     * <p>代理会在玩家连接下游服务器时，把注册表中的通道主动声明给下游，使下游插件能够向代理
     * 发送该通道上的插件消息。未注册的通道会被下游（Paper 的 {@code CraftPlayer#sendPluginMessage}）
     * 静默丢弃，代理侧完全收不到。
     *
     * <p>通道命名空间应与插件 ID 一致，便于插件卸载时按命名空间批量清理，
     * 也与代理自身的通道命名约定统一。
     *
     * @param channels 待注册的通道
     * @throws IllegalArgumentException 若任一通道位于保留命名空间，见
     *                                  {@link io.slidermc.starlight.api.channel.ChannelRegistry#RESERVED_NAMESPACE}
     */
    void registerChannel(Key... channels);

    /**
     * 注销插件消息通道。
     *
     * @param channels 待注销的通道
     */
    void unregisterChannel(Key... channels);

    /**
     * 读取插件<b>自身</b>JAR内的资源。
     *
     * <p>与直接使用 {@code getClass().getClassLoader().getResourceAsStream(...)} 不同，
     * 该方法保证不会读到代理或其它插件的同名资源。插件打包的默认配置文件
     * （如 {@code config.yml}）应通过此方法读取。
     *
     * <p><b>该约定仅对基于 JAR 的插件成立。</b>内存插件（{@link PluginBase} 及其子类）没有
     * 独立的 JAR，因此没有"自身资源"这一概念，其实现返回 {@code null}；
     * 需要读取类路径资源的内存插件请自行使用 {@code getClass().getClassLoader()}。
     *
     * <p>调用方负责关闭返回的流。
     *
     * @param path 资源路径，相对于JAR根目录，可带或不带前导 {@code /}
     * @return 资源流；资源不存在，或该插件不是 JAR 插件时返回 {@code null}
     */
    InputStream getResourceAsStream(String path);
}
