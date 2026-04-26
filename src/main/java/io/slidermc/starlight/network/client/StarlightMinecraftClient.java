package io.slidermc.starlight.network.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.client.handler.StarlightClientHandler;
import io.slidermc.starlight.network.codec.ClientPacketDecoder;
import io.slidermc.starlight.network.codec.ClientPacketEncoder;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.packets.serverbound.handshake.ServerboundHandshakePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginStartPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StarlightMinecraftClient {
    private static final Logger log = LoggerFactory.getLogger(StarlightMinecraftClient.class);
    private final InetSocketAddress address;
    private final PacketRegistry packetRegistry;
    private final StarlightProxy proxy;

    private volatile Channel channel;

    private volatile CompletableFuture<LoginResult> loginFuture;

    private volatile boolean isConnected = false;

    /** Filled in by login() before the first non-HANDSHAKE packet is sent/received. */
    private volatile ProtocolVersion protocolVersion;
    private volatile ProtocolState outboundState;
    private volatile ProtocolState inboundState;
    /** The upstream player channel paired with this downstream connection. Set externally after login completes. */
    private volatile Channel playerChannel;
    /** True when this client is being used for a server switch; suppresses upstream player disconnect on login failure. */
    private volatile boolean switching = false;

    public StarlightMinecraftClient(InetSocketAddress address, PacketRegistry packetRegistry, StarlightProxy proxy) {
        this.address = address;
        this.packetRegistry = packetRegistry;
        this.proxy = proxy;
    }

    public CompletableFuture<Void> connectAsync() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        StarlightMinecraftClient client = this;

        Thread.startVirtualThread(() -> {
            EventLoopGroup group = new NioEventLoopGroup(1);
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                socketChannel.pipeline().addLast(InternalConfig.HANDLER_DECODER, new ClientPacketDecoder(packetRegistry, client));
                                socketChannel.pipeline().addLast(InternalConfig.HANDLER_ENCODER, new ClientPacketEncoder(packetRegistry, client));
                                socketChannel.pipeline().addLast(InternalConfig.HANDLER_MAIN, new StarlightClientHandler(packetRegistry, proxy, client));
                            }
                        });

                ChannelFuture f = bootstrap.connect(address).sync();
                log.debug("连接服务器: {}", address);

                channel = f.channel();

                DownstreamConnectionContext downstreamConnectionContext = new DownstreamConnectionContext();
                downstreamConnectionContext.setClient(this);
                channel.attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).set(downstreamConnectionContext);

                isConnected = true;
                future.complete(null);
                f.channel().closeFuture().sync();
                isConnected = false;
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                group.shutdownGracefully();
            }
        });
        return future;
    }

    public void disconnect() {
        if (channel != null) {
            channel.close();
        }
    }

    /**
     * 原子性完成登录。若 loginFuture 尚未完成则填入 result 并返回 true；
     * 若已由其他路径完成则返回 false。
     */
    public boolean completeLogin(LoginResult result) {
        CompletableFuture<LoginResult> f = loginFuture;
        if (f == null) {
            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.client.complete_login_before_login"),
                    StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass().getName());
            return false;
        }
        return f.complete(result);
    }

    /**
     * 登录是否仍在进行中（未成功也未失败）。
     */
    public boolean isLoginPending() {
        CompletableFuture<LoginResult> f = loginFuture;
        return f != null && !f.isDone();
    }

    public CompletableFuture<LoginResult> login(ProtocolVersion protocolVersion, String name, UUID uuid) {
        if (channel == null) {
            return CompletableFuture.completedFuture(new LoginResult.Error(
                    new IllegalStateException("Channel not connected")));
        }
        loginFuture = new CompletableFuture<>();
        try {
            channel.writeAndFlush(new ServerboundHandshakePacket(
                    protocolVersion.getProtocolVersionCode(),
                    address.getHostName(),
                    (short) address.getPort(),
                    2
            )).addListener(_ -> {
                this.protocolVersion = protocolVersion;
                inboundState = ProtocolState.LOGIN;
                outboundState = ProtocolState.LOGIN;
                channel.writeAndFlush(new ServerboundLoginStartPacket(name, uuid));
            });
        } catch (Exception e) {
            completeLogin(new LoginResult.Error(e));
        }
        return loginFuture;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public PacketRegistry getPacketRegistry() {
        return packetRegistry;
    }

    public StarlightProxy getProxy() {
        return proxy;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public ProtocolState getOutboundState() {
        return outboundState;
    }

    public void setOutboundState(ProtocolState outboundState) {
        this.outboundState = outboundState;
    }

    public ProtocolState getInboundState() {
        return inboundState;
    }

    public void setInboundState(ProtocolState inboundState) {
        this.inboundState = inboundState;
    }

    public Channel getPlayerChannel() {
        return playerChannel;
    }

    public void setPlayerChannel(Channel playerChannel) {
        this.playerChannel = playerChannel;
    }

    public boolean isSwitching() {
        return switching;
    }

    public void setSwitching(boolean switching) {
        this.switching = switching;
    }
}
