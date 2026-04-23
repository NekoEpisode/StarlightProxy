package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.DisconnectUtils;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientboundEncryptionRequestPacket implements IMinecraftPacket {
    private String serverId;
    private byte[] publicKey;
    private byte[] verifyToken;
    private boolean shouldAuthenticate;

    public ClientboundEncryptionRequestPacket() {}

    public ClientboundEncryptionRequestPacket(String serverId, byte[] publicKey, byte[] verifyToken, boolean shouldAuthenticate) {
        this.serverId = serverId;
        this.publicKey = publicKey;
        this.verifyToken = verifyToken;
        this.shouldAuthenticate = shouldAuthenticate;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, serverId);
        MinecraftCodecUtils.writeByteArray(byteBuf, publicKey);
        MinecraftCodecUtils.writeByteArray(byteBuf, verifyToken);
        byteBuf.writeBoolean(shouldAuthenticate);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.serverId = MinecraftCodecUtils.readString(byteBuf);
        this.publicKey = MinecraftCodecUtils.readByteArray(byteBuf);
        this.verifyToken = MinecraftCodecUtils.readByteArray(byteBuf);
        this.shouldAuthenticate = byteBuf.readBoolean();
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public boolean isShouldAuthenticate() {
        return shouldAuthenticate;
    }

    public void setShouldAuthenticate(boolean shouldAuthenticate) {
        this.shouldAuthenticate = shouldAuthenticate;
    }

    public static class Listener implements IPacketListener<ClientboundEncryptionRequestPacket> {
        private static final Logger log = LoggerFactory.getLogger(Listener.class);

        @Override
        public void handle(ClientboundEncryptionRequestPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // 下游把Encryption发来了，应该是配置错误
            // Starlight目前不支持与正版模式下游建立加密，直接断开玩家并提示
            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.downstream_sent_encryption_request"));

            DownstreamConnectionContext downstreamCtx = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            if (downstreamCtx != null && downstreamCtx.getClient() != null) {
                StarlightMinecraftClient client = downstreamCtx.getClient();
                // If this downstream connection was created for a server switch, fail the new-client login with a
                // specific reason so ModernServerSwitcher can present it to the player without disconnecting them.
                if (client.isSwitching()) {
                    Channel playerChannel = client.getPlayerChannel();
                    if (playerChannel != null) {
                        ConnectionContext connCtx = playerChannel.attr(AttributeKeys.CONNECTION_CONTEXT).get();
                        if (connCtx != null) {
                            client.failLogin(
                                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                                            connCtx.getTranslation("starlight.disconnect.login_failed").replace("<error_msg>",
                                                    connCtx.getTranslation("starlight.disconnect.backend_requires_online_mode"))
                                    )
                            );
                        } else {
                            // Fallback: fail login with a plain reason (translated by proxy)
                            client.failLogin(MiniMessageUtils.MINI_MESSAGE.deserialize(
                                    proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode")
                            ));
                        }
                    } else {
                        client.failLogin(MiniMessageUtils.MINI_MESSAGE.deserialize(
                                proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode")
                        ));
                    }
                    // Close downstream only
                    ctx.channel().close();
                    return;
                }

                // Not switching: initial login — forward disconnect to player and close both sides.
                DisconnectUtils.forwardAndClose(
                        client,
                        MiniMessage.miniMessage().deserialize(
                                proxy.getTranslateManager().translate("starlight.disconnect.login_failed")
                                        .replace("<error_msg>", proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode"))
                        ),
                        proxy
                );
            }
            // 关闭下游连接
            ctx.channel().close();
        }
    }
}
