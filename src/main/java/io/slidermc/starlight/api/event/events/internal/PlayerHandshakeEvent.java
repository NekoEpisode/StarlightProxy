package io.slidermc.starlight.api.event.events.internal;

import io.netty.channel.Channel;
import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.protocolenum.NextState;

import java.net.InetSocketAddress;

/**
 * 在收到客户端握手包（{@code minecraft:intention}）后立即触发。
 *
 * <p>此刻玩家尚未认证、{@link io.slidermc.starlight.api.player.ProxiedPlayer} 也还未创建，
 * 因此事件不实现 {@link io.slidermc.starlight.api.event.events.interfaces.IPlayerEvent}。
 *
 * <p><b>下游握手地址</b>：代理连接后端服务器时会自行构造一个新的握手包，其地址默认取后端
 * 配置中的地址，客户端请求的域名（以及附加在该字段上的其它数据，例如 Geyser 为 Floodgate
 * 写入的加密串）会被丢弃。本事件提供了改写这个地址的入口：
 *
 * <pre>{@code
 * @EventHandler
 * public void onHandshake(PlayerHandshakeEvent event) {
 *     // 默认值就是客户端请求的地址（已剥离 ?query），可在其基础上追加数据
 *     event.setDownstreamAddress(event.getDownstreamAddress() + '\0' + encryptedData);
 * }
 * }</pre>
 *
 * <p>未调用 {@link #setDownstreamAddress(String)} 时，下游握手地址取决于
 * {@code pass-through-hostname} 配置，见
 * {@link io.slidermc.starlight.config.StarlightConfig#isPassThroughHostname()}。
 *
 * <p>本事件同步派发，运行在客户端连接的事件循环上，监听器应避免阻塞。
 */
public class PlayerHandshakeEvent implements IStarlightEvent {

    private final ConnectionContext connection;
    private final String rawAddress;
    private final short port;
    private final NextState nextState;

    private volatile String downstreamAddress;
    private volatile boolean downstreamAddressOverridden;

    /**
     * @param connection              上游玩家连接的上下文，可为 {@code null}
     * @param rawAddress              客户端握手包中的原始 {@code serverAddress}，未做任何切分
     * @param port                    客户端握手包中的端口
     * @param nextState               客户端请求的下一状态
     * @param defaultDownstreamAddress 未改写时下游握手将使用的地址；为 {@code null} 表示沿用
     *                                 后端配置中的地址
     */
    public PlayerHandshakeEvent(ConnectionContext connection, String rawAddress, short port, NextState nextState,
                                String defaultDownstreamAddress) {
        this.connection = connection;
        this.rawAddress = rawAddress;
        this.port = port;
        this.nextState = nextState;
        this.downstreamAddress = defaultDownstreamAddress;
    }

    /**
     * @return 上游玩家连接的上下文；不可用时为 {@code null}
     */
    public ConnectionContext getConnection() {
        return connection;
    }

    /**
     * @return 上游玩家连接；不可用时为 {@code null}
     */
    public Channel getChannel() {
        return connection != null ? connection.getChannel() : null;
    }

    /**
     * @return 客户端连接的远端地址；不可用时为 {@code null}
     */
    public InetSocketAddress getRemoteAddress() {
        Channel channel = getChannel();
        return channel != null && channel.remoteAddress() instanceof InetSocketAddress address ? address : null;
    }

    /**
     * @return 客户端握手包中的原始 {@code serverAddress}，包含其中的任何分隔数据（如 {@code \0} 之后的段）
     */
    public String getRawAddress() {
        return rawAddress;
    }

    /**
     * 客户端握手包中原始地址去掉 {@code ?query} 之后的部分。
     *
     * <p>可安全地作为下游握手地址的基础：不含代理侧的虚拟主机参数，
     * 但仍保留 {@code \0} 之后附加的数据段。
     *
     * @return 干净地址；{@link #getRawAddress()} 为 {@code null} 时同样返回 {@code null}
     */
    public String getCleanAddress() {
        if (rawAddress == null) {
            return null;
        }
        int question = rawAddress.indexOf('?');
        return question < 0 ? rawAddress : rawAddress.substring(0, question);
    }

    /**
     * @return 客户端握手包中的端口
     */
    public short getPort() {
        return port;
    }

    /**
     * @return 客户端请求的下一状态，可用于区分服务器列表查询（{@link NextState#STATUS}）与登录
     */
    public NextState getNextState() {
        return nextState;
    }

    /**
     * 当前生效的下游握手地址。
     *
     * <p>初始值由 {@code pass-through-hostname} 配置决定；透传开启时为客户端请求的地址，
     * 关闭时为 {@code null}（表示沿用后端配置中的地址）。
     *
     * @return 即将用于下游握手的地址；为 {@code null} 表示沿用后端配置地址
     */
    public String getDownstreamAddress() {
        return downstreamAddress;
    }

    /**
     * 改写下游握手地址。
     *
     * <p>多次调用以最后一次为准。
     *
     * <p>传入 {@code null} 表示显式撤销改写：此时下游地址回到
     * {@code pass-through-hostname} 配置所决定的值——透传开启时为客户端请求的地址，
     * 关闭时为"沿用后端配置地址"。若本意是强制沿用后端配置地址，请显式改写或关闭透传。
     *
     * @param downstreamAddress 新的下游握手地址
     */
    public void setDownstreamAddress(String downstreamAddress) {
        this.downstreamAddress = downstreamAddress;
        this.downstreamAddressOverridden = true;
    }

    /**
     * @return 下游握手地址是否已被本事件的监听器改写
     */
    public boolean isDownstreamAddressOverridden() {
        return downstreamAddressOverridden;
    }
}
