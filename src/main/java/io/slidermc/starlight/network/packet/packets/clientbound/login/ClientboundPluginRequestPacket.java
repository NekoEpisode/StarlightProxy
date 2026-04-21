package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundPluginResponsePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class ClientboundPluginRequestPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundPluginRequestPacket.class);
    private int messageId;
    private Key key;
    private byte[] data;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, messageId);
        MinecraftCodecUtils.writeKey(byteBuf, key);
        byteBuf.writeBytes(data);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.messageId = MinecraftCodecUtils.readVarInt(byteBuf);
        this.key = MinecraftCodecUtils.readKey(byteBuf);
        this.data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(this.data);
    }

    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public static class Listener implements IPacketListener<ClientboundPluginRequestPacket> {
        private static final Key VELOCITY_PLAYER_INFO = Key.key("velocity:player_info");

        @Override
        public void handle(ClientboundPluginRequestPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            log.debug("收到下游plugin request: {}", packet.key);

            DownstreamConnectionContext downstreamCtx = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            ConnectionContext context = downstreamCtx.getClient().getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();

            if (!packet.key.equals(VELOCITY_PLAYER_INFO)) {
                return;
            }

            if (!"modern".equals(proxy.getConfig().getForwardType())) {
                // 下游要求 Modern Forwarding，但代理未启用
                if (downstreamCtx.getClient().isSwitching()) {
                    // 切换服务器场景：用具体原因失败登录，由 ModernServerSwitcher.exceptionally 展示给玩家
                    log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.forwarding.downstream_requires_modern_switch"));
                    downstreamCtx.getClient().failLogin(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    context.getTranslation("starlight.switching.error.downstream_requires_modern_forwarding")
                            )
                    );
                } else {
                    // 初始登录场景：踢出玩家
                    log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.forwarding.downstream_requires_modern_login"));
                    downstreamCtx.getClient().getPlayerChannel().writeAndFlush(
                            new ClientboundDisconnectConfigurationPacket(
                                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                                            context.getTranslation("starlight.disconnect.downstream_requires_modern_forwarding")
                                    )
                            )
                    ).addListener(_ -> ctx.channel().close());
                }
                return;
            }

            // 读取下游请求的版本号，最高支持 v4
            int originalVersion = packet.data.length > 0 ? (packet.data[0] & 0xFF) : 1;
            // byte version = (byte) Math.min(originalVersion, 4);
            byte version = 4; // TODO: 目前只支持v4
            log.debug("velocity:player_info 请求的原version: v{}, 实际使用 v{}", originalVersion, version);

            if (!(downstreamCtx.getClient().getPlayerChannel().remoteAddress() instanceof InetSocketAddress remoteAddr)) {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.forwarding.non_inet_address"));
                downstreamCtx.getClient().failLogin(
                        MiniMessageUtils.MINI_MESSAGE.deserialize(
                                context.getTranslation("starlight.error.forwarding.non_inet_address")
                        )
                );
                return;
            }

            ProxiedPlayer player = context.getPlayer().asProxiedPlayer().orElse(null);
            if (player == null) {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.forwarding.player_not_found"));
                downstreamCtx.getClient().failLogin(
                        MiniMessageUtils.MINI_MESSAGE.deserialize(
                                context.getTranslation("starlight.error.forwarding.player_not_found")
                        )
                );
                return;
            }

            String address = remoteAddr.getAddress().getHostAddress();
            UUID uuid = player.getGameProfile().uuid();
            String playerName = player.getGameProfile().username();
            List<GameProfile.Property> properties = player.getGameProfile().properties();

            // 构建 data 部分（version + address + uuid + name + properties）
            ByteBuf dataBuf = ctx.alloc().buffer();
            try {
                dataBuf.writeByte(version);
                MinecraftCodecUtils.writeString(dataBuf, address);
                MinecraftCodecUtils.writeUUID(dataBuf, uuid);
                MinecraftCodecUtils.writeString(dataBuf, playerName);
                MinecraftCodecUtils.writeProperties(dataBuf, properties);

                byte[] dataBytes = new byte[dataBuf.readableBytes()];
                dataBuf.readBytes(dataBytes);

                // 计算 HMAC
                byte[] hmac;
                try {
                    String forwardSecret = proxy.getConfig().getForwardSecret();
                    if (forwardSecret == null || forwardSecret.isEmpty()) {
                        log.error(proxy.getTranslateManager().translate("starlight.logging.error.forwarding_secret_not_configured"));
                        downstreamCtx.getClient().failLogin(
                                MiniMessageUtils.MINI_MESSAGE.deserialize(
                                        context.getTranslation("starlight.error.forwarding.secret_not_configured")
                                )
                        );
                        return;
                    }
                    hmac = proxy.getEncryptionManager().computeVelocityHmac(
                            forwardSecret.getBytes(StandardCharsets.UTF_8),
                            dataBytes
                    );
                } catch (Exception e) {
                    log.error(proxy.getTranslateManager().translate("starlight.logging.error.compute_velocity_hmac_failed"), e);
                    downstreamCtx.getClient().failLogin(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    context.getTranslation("starlight.error.forwarding.hmac_computation_failed")
                            )
                    );
                    return;
                }

                // 拼接 HMAC + data，避免额外 ByteBuf 分配
                byte[] responseData = new byte[hmac.length + dataBytes.length];
                System.arraycopy(hmac, 0, responseData, 0, hmac.length);
                System.arraycopy(dataBytes, 0, responseData, hmac.length, dataBytes.length);

                ctx.channel().writeAndFlush(new ServerboundPluginResponsePacket(packet.messageId, true, responseData));
                log.debug("已发送 velocity:player_info response (v{})", version);
            } finally {
                dataBuf.release();
            }
        }
    }
}
