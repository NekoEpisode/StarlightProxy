package io.slidermc.starlight.network.packet.packets.clientbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.client.LoginResult;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.EncryptionDecoder;
import io.slidermc.starlight.network.codec.EncryptionEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundEncryptionResponsePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.DisconnectUtils;
import io.slidermc.starlight.utils.MiniMessageUtils;
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
            DownstreamConnectionContext downstreamCtx = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            if (downstreamCtx == null || downstreamCtx.getClient() == null) {
                ctx.channel().close();
                return;
            }
            StarlightMinecraftClient client = downstreamCtx.getClient();

            if (!packet.isShouldAuthenticate()) {
                // 仅加密不验证：正常完成加密握手
                handleEncryptionOnly(packet, ctx, proxy, client);
                return;
            }

            // shouldAuthenticate = true：代理无法替玩家过 Mojang 验证，必须断连
            if (client.isSwitching()) {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.downstream_sent_encryption_request_switch"));
                Channel playerChannel = client.getPlayerChannel();
                if (playerChannel != null) {
                    ConnectionContext connCtx = playerChannel.attr(AttributeKeys.CONNECTION_CONTEXT).get();
                    String reasonKey = connCtx != null
                            ? connCtx.getTranslation("starlight.disconnect.login_failed")
                                    .replace("<error_msg>", connCtx.getTranslation("starlight.disconnect.backend_requires_online_mode"))
                            : proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode");
                    client.completeLogin(new LoginResult.Kicked(
                            MiniMessageUtils.MINI_MESSAGE.deserialize(reasonKey)));
                } else {
                    client.completeLogin(new LoginResult.Kicked(MiniMessageUtils.MINI_MESSAGE.deserialize(
                            proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode"))));
                }
                ctx.channel().close();
                return;
            }

            // Initial login: forward disconnect to player and close both sides.
            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.downstream_sent_encryption_request"));
            DisconnectUtils.forwardAndClose(
                    client,
                    MiniMessageUtils.MINI_MESSAGE.deserialize(
                            proxy.getTranslateManager().translate("starlight.disconnect.login_failed")
                                    .replace("<error_msg>", proxy.getTranslateManager().translate("starlight.disconnect.backend_requires_online_mode"))
                    ),
                    proxy
            );
            ctx.channel().close();
        }

        private void handleEncryptionOnly(ClientboundEncryptionRequestPacket packet, ChannelHandlerContext ctx,
                                           StarlightProxy proxy, StarlightMinecraftClient client) {
            log.debug("下游要求加密但不需要验证，执行离线加密");
            try {
                var em = proxy.getEncryptionManager();
                byte[] sharedSecret = em.generateSharedSecret();
                byte[] encryptedSecret = io.slidermc.starlight.manager.EncryptionManager.encryptRSA(sharedSecret, packet.getPublicKey());
                byte[] encryptedToken = io.slidermc.starlight.manager.EncryptionManager.encryptRSA(packet.getVerifyToken(), packet.getPublicKey());

                ctx.channel().writeAndFlush(new ServerboundEncryptionResponsePacket(encryptedSecret, encryptedToken))
                        .addListener(f -> {
                            if (!f.isSuccess()) {
                                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.downstream_encryption_response_failed"));
                                ctx.channel().close();
                                return;
                            }
                            try {
                                ctx.pipeline().addBefore(
                                        InternalConfig.HANDLER_DECODER,
                                        InternalConfig.HANDLER_DECRYPT,
                                        new EncryptionDecoder(em.createDecryptCipher(sharedSecret)));
                                ctx.pipeline().addBefore(
                                        InternalConfig.HANDLER_ENCODER,
                                        InternalConfig.HANDLER_ENCRYPT,
                                        new EncryptionEncoder(em.createEncryptCipher(sharedSecret)));
                                log.debug("下游加密通道已启用");
                            } catch (Exception e) {
                                log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.pipeline_install_failed"), e);
                                ctx.channel().close();
                            }
                        });
            } catch (Exception e) {
                log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.pipeline_install_failed"), e);
                ctx.channel().close();
            }
        }
    }
}
