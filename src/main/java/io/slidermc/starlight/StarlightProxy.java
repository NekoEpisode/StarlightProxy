package io.slidermc.starlight;

import com.mojang.brigadier.CommandDispatcher;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.slidermc.starlight.api.command.CommandManager;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.event.EventManager;
import io.slidermc.starlight.api.player.PlayerManager;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.executor.ProxyExecutors;
import io.slidermc.starlight.manager.EncryptionManager;
import io.slidermc.starlight.manager.ServerManager;
import io.slidermc.starlight.network.codec.ServerPacketDecoder;
import io.slidermc.starlight.network.codec.ServerPacketEncoder;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.server.handler.StarlightServerHandler;
import io.slidermc.starlight.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;

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
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        try {
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
            log.info(translateManager.translate("starlight.logging.info.server.starting"), address.getHostString(), address.getPort());
            channelFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
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
}
