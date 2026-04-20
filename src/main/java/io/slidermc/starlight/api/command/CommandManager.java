package io.slidermc.starlight.api.command;

import com.mojang.brigadier.CommandDispatcher;
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
 */
public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final CommandDispatcher<IStarlightCommandSource> dispatcher;
    /** 已注册的命令，key 为命令名（小写）。 */
    private final Map<String, StarlightCommand> commands = new ConcurrentHashMap<>();

    private final StarlightProxy proxy;

    public CommandManager(CommandDispatcher<IStarlightCommandSource> dispatcher, StarlightProxy proxy) {
        this.dispatcher = dispatcher;
        this.proxy = proxy;
    }

    /**
     * 注册一个命令。若同名命令已存在则覆盖。
     */
    public void register(StarlightCommand command) {
        dispatcher.register(command.build());
        commands.put(command.getName(), command);
        log.debug("已注册命令: /{}", command.getName());
    }

    /**
     * 注销一个命令（从代理命令树中移除）。
     */
    public void unregister(String name) {
        String lower = name.toLowerCase();
        if (commands.remove(lower) != null) {
            dispatcher.getRoot().getChildren()
                    .removeIf(node -> lower.equals(node.getName()));
            log.debug("已注销命令: /{}", lower);
        }
    }

    /**
     * 是否已注册该命令名。
     */
    public boolean hasCommand(String name) {
        return dispatcher.getRoot().getChild(name) != null;
    }

    /**
     * 以指定 source 执行命令字符串（不含 '/'）。
     *
     * @return Brigadier 返回值；执行失败时返回 0
     */
    public int execute(String input, IStarlightCommandSource source) {
        try {
            return dispatcher.execute(input, source);
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


