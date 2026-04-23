package io.slidermc.starlight.api.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理命令管理器，封装 Brigadier {@link CommandDispatcher}。
 *
 * <p>Brigadier 的 {@code CommandDispatcher} 不是线程安全的，因此所有对 dispatcher 的
 * 注册、注销操作以及命令解析均通过 {@link #lock} 序列化。命令处理函数的执行在锁外进行，
 * 以避免处理函数回调 {@code CommandManager} 时产生死锁。
 *
 * <p><b>重要：</b>命令处理函数在执行期间不得重新进入本管理器的 {@code register}/{@code unregister}
 * 方法，否则可能导致死锁。
 */
public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final CommandDispatcher<IStarlightCommandSource> dispatcher;
    /** 已注册的命令，key 为命令名（小写）。 */
    private final Map<String, StarlightCommand> commands = new ConcurrentHashMap<>();

    private final StarlightProxy proxy;

    /** 保护 Brigadier {@code CommandDispatcher} 的互斥锁，注册/注销/解析均需持有。 */
    private final Object lock = new Object();

    public CommandManager(CommandDispatcher<IStarlightCommandSource> dispatcher, StarlightProxy proxy) {
        this.dispatcher = dispatcher;
        this.proxy = proxy;
    }

    /**
     * 注册一个命令。若同名命令已存在则覆盖。
     */
    public void register(StarlightCommand command) {
        synchronized (lock) {
            dispatcher.register(command.build());
        }
        commands.put(command.getName(), command);
        log.debug("已注册命令: /{}", command.getName());
    }

    /**
     * 注销一个命令（从代理命令树中移除）。
     */
    public void unregister(String name) {
        String lower = name.toLowerCase();
        boolean removed;
        synchronized (lock) {
            removed = commands.remove(lower) != null;
            if (removed) {
                dispatcher.getRoot().getChildren()
                        .removeIf(node -> lower.equals(node.getName()));
            }
        }
        if (removed) {
            log.debug("已注销命令: /{}", lower);
        }
    }

    /**
     * 是否已注册该命令名。
     */
    public boolean hasCommand(String name) {
        synchronized (lock) {
            return dispatcher.getRoot().getChild(name) != null;
        }
    }

    /**
     * 以指定 source 执行命令字符串（不含 '/'）。
     *
     * <p>命令解析在 {@link #lock} 下进行以保证线程安全，但命令处理函数的执行
     * 在释放锁之后进行，以避免处理函数回调 {@code CommandManager} 时死锁。
     *
     * @return Brigadier 返回值；执行失败时返回 0
     */
    public int execute(String input, IStarlightCommandSource source) {
        ParseResults<IStarlightCommandSource> parse;
        synchronized (lock) {
            parse = dispatcher.parse(input, source);
        }
        try {
            return dispatcher.execute(parse);
        } catch (CommandSyntaxException e) {
            source.sendMessage(net.kyori.adventure.text.Component.text(e.getMessage()));
            return 0;
        } catch (Exception e) {
            log.error(proxy.getTranslateManager().translate("starlight.logging.error.error_on_executing_command"), input, e);
            return 0;
        }
    }

    /**
     * 获取底层 {@link CommandDispatcher}，供需要直接操作的场景使用。
     */
    public CommandDispatcher<IStarlightCommandSource> getDispatcher() {
        return dispatcher;
    }

    /**
     * 返回所有已注册命令的不可变视图。
     */
    public Collection<StarlightCommand> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }
}


