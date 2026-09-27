package io.slidermc.starlight.network.context;

import io.netty.channel.Channel;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.data.clientinformation.ClientInformation;
import io.slidermc.starlight.network.command.CommandNodeData;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundCommandsPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundDisconnectPlayPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上游（玩家客户端）连接的上下文信息。
 *
 * <p>此类中的字段均为 {@code volatile}，因为可能从 Netty 上游 EventLoop、下游 EventLoop、
 * 事件线程池等多个线程访问。对于引用可变对象（如 {@code byte[]}）的字段，getter/setter
 * 使用防御性拷贝以确保发布后不会被外部修改。
 */
public class ConnectionContext {
    private static final Logger log = LoggerFactory.getLogger(ConnectionContext.class);

    private volatile HandshakeInformation handshakeInformation;
    private volatile ProtocolState inboundState;
    private volatile ProtocolState outboundState;
    private volatile ProxiedPlayer player;
    /** The downstream server channel paired with this player connection. Set externally when the player is connected to a backend server. */
    private volatile Channel downstreamChannel;
    /** Set by ModernServerSwitcher before sending StartConfiguration; completed by ServerboundConfigurationAckPacket.Listener. */
    private volatile CompletableFuture<Void> pendingReconfiguration;
    private volatile ClientInformation clientInformation;
    /**
     * 客户端握手包中的原始 {@code serverAddress}，不做任何切分。
     *
     * <p>不能复用 {@link HandshakeInformation#getServerHost()}：{@link ServerHost#parseFrom(String)}
     * 会按 {@code ?} 拆分，而该字段里可能附带以 {@code \0} 分隔的外部数据（例如 Geyser 写入的
     * Floodgate 加密串），其内容完全可能包含 {@code ?} 或 {@code &}。
     *
     * <p>为 {@code null} 表示本次连接不透传客户端地址，下游握手沿用后端配置中的地址。
     */
    private volatile String handshakeAddress;
    /** 插件指定的下游握手地址，优先级高于 {@link #handshakeAddress}；为 {@code null} 表示未指定。 */
    private volatile String downstreamAddress;
    private volatile byte[] verifyToken;
    /** 正版验证流程中暂存的用户名，EncryptionResponse.Listener 使用后可清除 */
    private volatile String pendingUsername;
    /** 当 PreLoginEvent 强制此连接走正版验证时设为 true，覆盖全局 offline-mode 配置 */
    private volatile boolean perConnectionOnlineMode;
    /** 当 PreLoginEvent 强制此连接跳过 Mojang 验证时设为 true，覆盖全局 online-mode 配置 */
    private volatile boolean perConnectionOfflineMode;

    /** 客户端通过 minecraft:register 声明过的插件消息通道 */
    private final Set<Key> clientChannels = ConcurrentHashMap.newKeySet();

    /** 已下发给当前下游连接的通道，用于判断注册表变化后是否需要补发 */
    private final Set<Key> announcedChannels = ConcurrentHashMap.newKeySet();

    /** 后端命令树的深拷贝缓存，用于权限更新后重建命令树 */
    private volatile List<CommandNodeData> cachedCommandNodes;
    private volatile int cachedCommandRootIndex;

    private final Channel channel;

    private final StarlightProxy proxy;

    public ConnectionContext(StarlightProxy proxy, Channel channel) {
        this.inboundState = ProtocolState.HANDSHAKE;
        this.outboundState = ProtocolState.HANDSHAKE;
        this.handshakeInformation = new HandshakeInformation();
        this.proxy = proxy;
        this.channel = channel;
    }

    public ProtocolState getInboundState() {
        return inboundState;
    }

    public void setInboundState(ProtocolState inboundState) {
        this.inboundState = inboundState;
    }

    public ProtocolState getOutboundState() {
        return outboundState;
    }

    public void setOutboundState(ProtocolState outboundState) {
        this.outboundState = outboundState;

        if (outboundState != ProtocolState.PLAY) {
            ProxiedPlayer p = this.player;
            if (p != null) {
                p.setCanSendMessages(false);
            }
        }
    }

    public HandshakeInformation getHandshakeInformation() {
        return handshakeInformation;
    }

    public void setHandshakeInformation(HandshakeInformation handshakeInformation) {
        this.handshakeInformation = handshakeInformation;
    }

    public ProxiedPlayer getPlayer() {
        return player;
    }

    /**
     * 返回上游玩家连接。登录阶段即可使用，因为在 ProxiedPlayer 创建之前
     * 插件只能通过它获取远程地址等连接信息。
     */
    public Channel getChannel() {
        return channel;
    }

    public void setPlayer(ProxiedPlayer player) {
        this.player = player;
    }

    public Channel getDownstreamChannel() {
        return downstreamChannel;
    }

    public void setDownstreamChannel(Channel downstreamChannel) {
        this.downstreamChannel = downstreamChannel;
        // 新下游对旧连接上的通道声明一无所知，必须重新下发
        this.announcedChannels.clear();
    }

    public CompletableFuture<Void> getPendingReconfiguration() {
        return pendingReconfiguration;
    }

    public void setPendingReconfiguration(CompletableFuture<Void> pendingReconfiguration) {
        this.pendingReconfiguration = pendingReconfiguration;
    }

    public Optional<ClientInformation> getClientInformation() {
        return Optional.ofNullable(clientInformation);
    }

    public void setClientInformation(ClientInformation clientInformation) {
        this.clientInformation = clientInformation;
    }

    /**
     * 记录客户端握手包中的原始地址，供后续构造下游握手时透传。
     *
     * @param handshakeAddress 原始 {@code serverAddress}；为 {@code null} 表示不透传
     */
    public void setHandshakeAddress(String handshakeAddress) {
        this.handshakeAddress = handshakeAddress;
    }

    /**
     * @return 客户端握手包中的原始地址；未记录或未启用透传时为 {@code null}
     */
    public String getHandshakeAddress() {
        return handshakeAddress;
    }

    /**
     * 指定下游握手使用的地址，覆盖客户端原始地址。
     *
     * @param downstreamAddress 下游握手地址；为 {@code null} 表示撤销指定，回退到客户端原始地址
     */
    public void setDownstreamAddress(String downstreamAddress) {
        this.downstreamAddress = downstreamAddress;
    }

    /**
     * 返回下游握手实际应使用的地址。
     *
     * <p>优先级：插件指定的 {@link #downstreamAddress} &gt; 客户端原始地址
     * {@link #handshakeAddress}。两者均不可用时返回 {@code null}，调用方应回退到后端配置中的地址。
     *
     * <p>客户端原始地址中的 {@code ?query} 部分会被剥离：它是代理侧的虚拟主机信息，
     * 不应出现在发给后端的握手里。
     *
     * @return 下游握手地址，或 {@code null} 表示沿用后端配置地址
     */
    public String getEffectiveDownstreamAddress() {
        String explicit = this.downstreamAddress;
        if (explicit != null) {
            return explicit;
        }

        String raw = this.handshakeAddress;
        if (raw == null) {
            return null;
        }

        int question = raw.indexOf('?');
        return question < 0 ? raw : raw.substring(0, question);
    }

    public byte[] getVerifyToken() {
        byte[] token = this.verifyToken;
        return token != null ? token.clone() : null;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken != null ? verifyToken.clone() : null;
    }

    public String getPendingUsername() {
        return pendingUsername;
    }

    public void setPendingUsername(String pendingUsername) {
        this.pendingUsername = pendingUsername;
    }

    public boolean isPerConnectionOnlineMode() {
        return perConnectionOnlineMode;
    }

    public void setPerConnectionOnlineMode(boolean perConnectionOnlineMode) {
        this.perConnectionOnlineMode = perConnectionOnlineMode;
    }

    /**
     * 此连接是否被强制跳过 Mojang 验证。
     *
     * <p>与 {@link #isPerConnectionOnlineMode()} 互斥：两者同时为 true 时离线优先，
     * 因为无法对一个没有 Mojang 会话的客户端（如基岩版）发起验证。
     *
     * <p>与加密无关：是否加密由全局 {@code encryption} 配置单独决定。
     *
     * @return 强制离线返回 true
     */
    public boolean isPerConnectionOfflineMode() {
        return perConnectionOfflineMode;
    }

    public void setPerConnectionOfflineMode(boolean perConnectionOfflineMode) {
        this.perConnectionOfflineMode = perConnectionOfflineMode;
    }

    /**
     * 记录客户端声明的一个插件消息通道。
     *
     * @param channel 通道
     * @return 若该通道此前未被记录则返回 true
     */
    public boolean addClientChannel(Key channel) {
        return clientChannels.add(channel);
    }

    /**
     * 移除客户端已注销的插件消息通道。
     *
     * @param channel 通道
     * @return 若该通道此前已被记录则返回 true
     */
    public boolean removeClientChannel(Key channel) {
        return clientChannels.remove(channel);
    }

    /**
     * @return 客户端声明过的通道数量的实时值，用于上限判断
     */
    public int getClientChannelCount() {
        return clientChannels.size();
    }

    /**
     * @return 客户端声明过的通道的不可变快照
     */
    public Set<Key> getClientChannels() {
        return Set.copyOf(clientChannels);
    }

    /**
     * 替换"已下发给当前下游"的通道集合。切换下游连接时需要先清空，因为新下游对旧连接的声明一无所知。
     *
     * @param channels 本次已下发的通道
     */
    public void setAnnouncedChannels(Set<Key> channels) {
        announcedChannels.clear();
        announcedChannels.addAll(channels);
    }

    /**
     * 计算相对已下发集合新增的通道。用于插件在玩家进入游戏后注册通道时补发给下游，
     * 否则下游会把这些通道上的消息当作未知通道丢弃。
     *
     * @param current 当前的完整通道集合
     * @return 尚未下发的通道，全部已下发时为空集
     */
    public Set<Key> diffAnnounced(Set<Key> current) {
        Set<Key> pending = new HashSet<>(current);
        pending.removeAll(announcedChannels);
        return pending;
    }

    public void cacheCommandTree(List<CommandNodeData> nodes, int rootIndex) {
        if (nodes == null) {
            this.cachedCommandNodes = null;
            return;
        }
        List<CommandNodeData> copy = new ArrayList<>(nodes.size());
        for (CommandNodeData node : nodes) {
            copy.add(new CommandNodeData(node));
        }
        this.cachedCommandNodes = copy;
        this.cachedCommandRootIndex = rootIndex;
    }

    public void refreshCommands() {
        List<CommandNodeData> cached = this.cachedCommandNodes;
        if (cached == null || cached.isEmpty()) return;

        ProxiedPlayer p = this.player;
        if (p == null) return;

        ClientboundCommandsPacket packet = new ClientboundCommandsPacket(proxy.getCommandArgumentTypeRegistry());
        packet.loadFromCache(cached, this.cachedCommandRootIndex);
        packet.mergeProxyCommands(
                proxy.getCommandDispatcher().getRoot(),
                proxy.getTranslateManager(),
                p
        );

        Channel playerChannel = p.getChannel();
        if (playerChannel != null) {
            playerChannel.writeAndFlush(packet);
        } else {
            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.player_channel_null_for_command_refresh"));
        }
    }

    public StarlightProxy getProxy() {
        return proxy;
    }

    public String getTranslation(String key) {
        String locale = (getClientInformation().isPresent() ? getClientInformation().get().getLocale() : proxy.getTranslateManager().getActiveLocale());
        return proxy.getTranslateManager().translate(locale, key);
    }

    public String getLocale() {
        return (getClientInformation().isPresent() ? getClientInformation().get().getLocale() : proxy.getTranslateManager().getActiveLocale());
    }

    public CompletableFuture<Void> toDownstream(IMinecraftPacket packet) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Channel downstream = this.downstreamChannel;
        if (downstream != null) {
            downstream.writeAndFlush(packet).addListener(ctx -> {
                if (ctx.isSuccess()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(ctx.cause());
                }
            });
        } else {
            future.completeExceptionally(new IllegalStateException("Downstream channel is null"));
        }
        return future;
    }

    public void kick(Component component) {
        Channel dc = this.downstreamChannel;
        if (dc != null && dc.isActive()) {
            dc.close().addListener(_ -> closeUpstream(component));
        } else {
            closeUpstream(component);
        }
    }

    private void closeUpstream(Component component) {
        if (outboundState == ProtocolState.LOGIN) {
            channel.writeAndFlush(new ClientboundDisconnectLoginPacket(component)).addListener(_ -> channel.close());
        } else if (outboundState == ProtocolState.CONFIGURATION) {
            channel.writeAndFlush(new ClientboundDisconnectConfigurationPacket(component)).addListener(_ -> channel.close());
        } else if (outboundState == ProtocolState.PLAY) {
            channel.writeAndFlush(new ClientboundDisconnectPlayPacket(component)).addListener(_ -> channel.close());
        } else {
            channel.close();
        }
    }
}
