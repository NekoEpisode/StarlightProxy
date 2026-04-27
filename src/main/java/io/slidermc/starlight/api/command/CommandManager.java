package io.slidermc.starlight.api.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理命令管理器，封装 Brigadier {@link CommandDispatcher}。
 *
 * <p>Brigadier 的 {@code CommandDispatcher} 不是线程安全的，因此所有对 dispatcher 的
 * 注册、注销操作以及命令解析均通过 {@link #lock} 序列化。
 *
 * <p><b>重要：</b>命令处理函数在执行期间不得重新进入本管理器的 {@code register}/{@code unregister}
 * 方法，否则可能导致死锁。
 */
public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final CommandDispatcher<IStarlightCommandSource> dispatcher;

    /** fullName (ns:name) → StarlightCommand */
    private final Map<String, StarlightCommand> commands = new ConcurrentHashMap<>();

    /** shortName (bare name) → fullName，记录短名归属 */
    private final Map<String, String> shortNames = new ConcurrentHashMap<>();

    /** namespace → Set<fullName>，用于批量注销 */
    private final Map<String, Set<String>> namespaceIndex = new ConcurrentHashMap<>();

    private final StarlightProxy proxy;
    private final Object lock = new Object();

    public CommandManager(CommandDispatcher<IStarlightCommandSource> dispatcher, StarlightProxy proxy) {
        this.dispatcher = dispatcher;
        this.proxy = proxy;
    }

    /**
     * 注册一个命令。若同名命令已存在则覆盖。
     *
     * <p>注册两个 Brigadier 节点：
     * <ol>
     *   <li>{@code namespace:name} — 真实命令节点，包含子节点和 executor</li>
     *   <li>{@code name} — 短名 redirect 别名（若短名未被占用）</li>
     * </ol>
     */
    public void register(StarlightCommand command) {
        String fullName = command.getName();
        String shortName = command.getDisplayName();
        String namespace = command.getNamespace();

        synchronized (lock) {
            dispatcher.register(command.build());
            commands.put(fullName, command);
            namespaceIndex.computeIfAbsent(namespace, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(fullName);

            var mainNode = dispatcher.getRoot().getChild(fullName);

            if (dispatcher.getRoot().getChild(shortName) == null) {
                shortNames.put(shortName, fullName);
                registerRedirect(shortName, mainNode);
            }

            for (String alias : command.getAliases()) {
                registerRedirect(alias, mainNode);
            }
        }
        log.debug("已注册命令: /{}", fullName);
    }

    /**
     * 注销一个命令。支持 fullName 或短名。
     */
    public void unregister(String name) {
        String lower = name.toLowerCase();
        synchronized (lock) {
            String fullName = commands.containsKey(lower) ? lower : shortNames.get(lower);
            if (fullName == null) return;

            StarlightCommand cmd = commands.remove(fullName);
            if (cmd == null) return;

            String shortName = cmd.getDisplayName();
            String namespace = cmd.getNamespace();

            dispatcher.getRoot().getChildren().removeIf(n -> fullName.equals(n.getName()));
            if (shortNames.remove(shortName) != null) {
                dispatcher.getRoot().getChildren().removeIf(n -> shortName.equals(n.getName()));
            }
            for (String alias : cmd.getAliases()) {
                dispatcher.getRoot().getChildren().removeIf(n -> alias.equals(n.getName()));
            }

            Set<String> nsSet = namespaceIndex.get(namespace);
            if (nsSet != null) {
                nsSet.remove(fullName);
                if (nsSet.isEmpty()) namespaceIndex.remove(namespace);
            }
        }
        log.debug("已注销命令: /{}", lower);
    }

    /**
     * 批量注销指定 namespace 下的所有命令。
     */
    public void unregisterAll(String namespace) {
        Set<String> names = namespaceIndex.remove(namespace);
        if (names == null || names.isEmpty()) return;
        List<String> copy = new ArrayList<>(names);
        for (String fullName : copy) {
            unregister(fullName);
        }
    }

    /**
     * 是否已注册该命令名（支持 fullName 或短名）。
     */
    public boolean hasCommand(String name) {
        synchronized (lock) {
            return dispatcher.getRoot().getChild(name.toLowerCase()) != null;
        }
    }

    /**
     * 以指定 source 执行命令字符串（不含 '/'）。
     */
    public int execute(String input, IStarlightCommandSource source) {
        ParseResults<IStarlightCommandSource> parse;
        synchronized (lock) {
            parse = dispatcher.parse(input, source);
        }
        try {
            return dispatcher.execute(parse);
        } catch (CommandSyntaxException e) {
            source.sendMessage(MiniMessageUtils.MINI_MESSAGE.deserialize(
                    proxy.getTranslateManager().translate("starlight.command.error"),
                    Placeholder.parsed("message", e.getMessage())));
            return 0;
        } catch (Exception e) {
            log.error(proxy.getTranslateManager().translate("starlight.logging.error.error_on_executing_command"), input, e);
            return 0;
        }
    }

    public CommandDispatcher<IStarlightCommandSource> getDispatcher() {
        return dispatcher;
    }

    /**
     * 返回所有已注册命令的不可变视图（去重，仅 real nodes）。
     */
    public Collection<StarlightCommand> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /**
     * 返回指定 namespace 下的所有命令。
     */
    public Collection<StarlightCommand> getCommands(String namespace) {
        Set<String> names = namespaceIndex.get(namespace);
        if (names == null) return Collections.emptyList();
        List<StarlightCommand> result = new ArrayList<>();
        for (String fullName : names) {
            StarlightCommand cmd = commands.get(fullName);
            if (cmd != null) result.add(cmd);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 根据名称查找已注册的命令（支持 fullName 或短名）。
     */
    public StarlightCommand getCommand(String name) {
        String lower = name.toLowerCase();
        StarlightCommand cmd = commands.get(lower);
        if (cmd != null) return cmd;
        String fullName = shortNames.get(lower);
        return fullName != null ? commands.get(fullName) : null;
    }

    private void registerRedirect(String alias, CommandNode<IStarlightCommandSource> target) {
        var builder = LiteralArgumentBuilder.<IStarlightCommandSource>literal(alias)
                .requires(target.getRequirement())
                .redirect(target);
        if (target.getCommand() != null) {
            builder.executes(target.getCommand());
        }
        dispatcher.register(builder);
    }
}
