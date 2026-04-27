package io.slidermc.starlight;

import com.mojang.brigadier.CommandDispatcher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.slidermc.starlight.api.command.CommandManager;
import io.slidermc.starlight.api.command.StarlightCommand;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.event.EventManager;
import io.slidermc.starlight.api.permission.PermissionService;
import io.slidermc.starlight.api.player.PlayerManager;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.command.console.ConsoleManager;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.executor.ProxyExecutors;
import io.slidermc.starlight.manager.EncryptionManager;
import io.slidermc.starlight.utils.MiniMessageUtils;
import io.slidermc.starlight.manager.ServerManager;
import io.slidermc.starlight.network.codec.ServerPacketDecoder;
import io.slidermc.starlight.network.codec.ServerPacketEncoder;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.server.handler.StarlightServerHandler;
import io.slidermc.starlight.permission.SimplePermissionManager;
import io.slidermc.starlight.plugin.PluginManager;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

public class StarlightProxy {
    private static final Logger log = LoggerFactory.getLogger(StarlightProxy.class);
    private final InetSocketAddress address;

    private final TranslateManager translateManager;
    private final PlayerManager playerManager;
    private final ServerManager serverManager;
    private final RegistryPacketUtils registryPacketUtils;
    private final StarlightConfig config;
    private final EncryptionManager encryptionManager;
    private final CommandDispatcher<IStarlightCommandSource> commandDispatcher = new CommandDispatcher<>();
    private final CommandManager commandManager = new CommandManager(commandDispatcher, this);
    private final ProxyExecutors executors = new ProxyExecutors();
    private final EventManager eventManager;
    private final PluginManager pluginManager;
    private volatile PermissionService permissionService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile ConsoleManager consoleManager;
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);

    public StarlightProxy(InetSocketAddress address, TranslateManager translateManager,
                          RegistryPacketUtils registryPacketUtils, StarlightConfig config,
                          ServerManager serverManager, PluginManager pluginManager) {
        this.address = address;
        this.translateManager = translateManager;
        this.registryPacketUtils = registryPacketUtils;
        this.config = config;
        this.playerManager = new PlayerManager();
        this.serverManager = serverManager;
        this.pluginManager = pluginManager;
        try {
            this.encryptionManager = new EncryptionManager();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize EncryptionManager", e);
        }
        eventManager = new EventManager(executors.getEventExecutor(), translateManager);
    }

    void start() {
        new Thread(() -> {
            Thread.currentThread().setName("Server Thread");
            try {
                startServer();
            } catch (Exception e) {
                log.error(translateManager.translate("starlight.logging.error.netty_server_start_failed"), e);
                System.exit(1);
            }
        }).start();

        log.info(translateManager.translate("starlight.logging.info.join_with_address"), address.getPort());
    }

    private void startServer() throws Exception {
        StarlightProxy proxy = this;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(4);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(InternalConfig.HANDLER_DECODER, new ServerPacketDecoder(
                                registryPacketUtils.getPacketRegistry(),
                                proxy
                        ));
                        socketChannel.pipeline().addLast(InternalConfig.HANDLER_ENCODER, new ServerPacketEncoder(
                                registryPacketUtils.getPacketRegistry()
                        ));
                        socketChannel.pipeline().addLast(InternalConfig.HANDLER_MAIN, new StarlightServerHandler(
                                registryPacketUtils.getPacketRegistry(),
                                proxy
                        ));
                    }
                });

        ChannelFuture channelFuture = serverBootstrap.bind(address).sync();
        serverChannel = channelFuture.channel();
        log.info(translateManager.translate("starlight.logging.info.server.starting"), address.getHostString(), address.getPort());
        serverChannel.closeFuture().sync();
    }

    public void shutdown() {
        if (!shutdownInitiated.compareAndSet(false, true)) return;

        log.info(translateManager.translate("starlight.logging.info.shutdown.stopping"));

        // 1. Stop accepting new connections
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.close();
        }

        // 2. Disconnect all players gracefully with a reason
        for (ProxiedPlayer player : playerManager.getPlayers()) {
            ConnectionContext ctx = player.getConnectionContext();
            if (ctx == null) continue;
            Component reason = MiniMessageUtils.MINI_MESSAGE.deserialize(
                    ctx.getTranslation("starlight.disconnect.shutdown"));
            player.kick(reason);
        }

        // 3. Shutdown Netty event loop groups synchronously (must be after all channel I/O completes)
        //    syncUninterruptibly() ensures pending writes (disconnect packets, etc.) are flushed
        //    before the JVM can exit, preventing "connection lost" on the client side.
        if (bossGroup != null) bossGroup.shutdownGracefully().syncUninterruptibly();
        if (workerGroup != null) workerGroup.shutdownGracefully().syncUninterruptibly();

        // 4. Disable all plugins (reverse order)
        pluginManager.disableAll();

        // 5. Stop permission manager (flush pending writes)
        if (permissionService instanceof SimplePermissionManager spm) {
            spm.stop();
        }

        // 6. Shutdown proxy thread pools
        executors.shutdown();

        log.info(translateManager.translate("starlight.logging.info.shutdown.complete"));

        // 7. Stop Log4j2 manually (shutdownHook="disable" in log4j2.xml,
        //    so we own the lifecycle and avoid races with JVM shutdown hook)
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.stop();

        // 8. Close console terminal LAST — releasing the terminal to the parent shell
        //    earlier would cause the PowerShell prompt to appear before all log output finishes.
        if (consoleManager != null) {
            consoleManager.close();
        }
    }

    /**
     * 设置控制台管理器，供 shutdown 时关闭终端。
     */
    public void setConsoleManager(ConsoleManager consoleManager) {
        this.consoleManager = consoleManager;
    }

    /**
     * 返回控制台管理器实例，可能为 null（控制台初始化失败时）。
     */
    public ConsoleManager getConsoleManager() {
        return consoleManager;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public StarlightConfig getConfig() {
        return config;
    }

    public RegistryPacketUtils getRegistryPacketUtils() {
        return registryPacketUtils;
    }

    public TranslateManager getTranslateManager() {
        return translateManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public CommandDispatcher<IStarlightCommandSource> getCommandDispatcher() {
        return commandDispatcher;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public void registerCommand(StarlightCommand command) {
        commandManager.register(command);
    }

    public ProxyExecutors getExecutors() {
        return executors;
    }

    /**
     * 返回插件管理器实例。
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * 返回事件管理器实例。
     */
    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * 返回当前的权限服务。
     */
    public PermissionService getPermissionService() {
        return permissionService;
    }

    /**
     * 设置权限服务。插件可在 {@code onEnable} 中替换为自定义实现。
     */
    public void setPermissionService(PermissionService permissionService) {
        if (this.permissionService != null && this.permissionService != permissionService) {
            log.warn(translateManager.translate("starlight.logging.warn.permission.replaced"),
                    this.permissionService.getClass().getName(),
                    permissionService.getClass().getName());
        }
        this.permissionService = permissionService;
    }
}
