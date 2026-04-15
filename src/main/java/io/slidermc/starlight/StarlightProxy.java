package io.slidermc.starlight;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.network.codec.MinecraftPacketDecoder;
import io.slidermc.starlight.network.codec.MinecraftPacketEncoder;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.server.handler.StarlightServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class StarlightProxy {
    private static final Logger log = LoggerFactory.getLogger(StarlightProxy.class);
    private final InetSocketAddress address;

    private final TranslateManager translateManager;
    private final RegistryPacketUtils registryPacketUtils;
    private final StarlightConfig config;

    public StarlightProxy(InetSocketAddress address, TranslateManager translateManager,
                          RegistryPacketUtils registryPacketUtils, StarlightConfig config) {
        this.address = address;
        this.translateManager = translateManager;
        this.registryPacketUtils = registryPacketUtils;
        this.config = config;
    }

    void start() {
        long start = System.currentTimeMillis();

        new Thread(() -> {
            Thread.currentThread().setName("Server Thread");
            try {
                startServer();
            } catch (Exception e) {
                log.error(translateManager.translate("starlight.logging.error.netty_server_start_failed"), e);
                System.exit(1);
            }
        }).start();

        log.info(translateManager.translate("starlight.logging.info.done"), (System.currentTimeMillis() - start));
        log.info(translateManager.translate("starlight.logging.info.join_with_address"), address.getPort());
    }

    private void startServer() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new MinecraftPacketDecoder(
                                    registryPacketUtils.getPacketRegistry()
                            ));
                            socketChannel.pipeline().addLast(new MinecraftPacketEncoder(
                                    registryPacketUtils.getPacketRegistry()
                            ));
                            socketChannel.pipeline().addLast(new StarlightServerHandler(
                                    registryPacketUtils.getPacketRegistry()
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
}
