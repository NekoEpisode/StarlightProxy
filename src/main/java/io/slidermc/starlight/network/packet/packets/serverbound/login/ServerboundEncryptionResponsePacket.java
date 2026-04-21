package io.slidermc.starlight.network.packet.packets.serverbound.login;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.codec.EncryptionDecoder;
import io.slidermc.starlight.network.codec.EncryptionEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.helper.LoginHelper;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ServerboundEncryptionResponsePacket implements IMinecraftPacket {
    private byte[] sharedSecret;
    private byte[] verifyToken;

    public ServerboundEncryptionResponsePacket() {}

    public ServerboundEncryptionResponsePacket(byte[] sharedSecret, byte[] verifyToken) {
        this.sharedSecret = sharedSecret;
        this.verifyToken = verifyToken;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeByteArray(byteBuf, sharedSecret);
        MinecraftCodecUtils.writeByteArray(byteBuf, verifyToken);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.sharedSecret = MinecraftCodecUtils.readByteArray(byteBuf);
        this.verifyToken = MinecraftCodecUtils.readByteArray(byteBuf);
    }

    public byte[] getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(byte[] sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public byte[] getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(byte[] verifyToken) {
        this.verifyToken = verifyToken;
    }

    public static class Listener implements IPacketListener<ServerboundEncryptionResponsePacket> {
        private static final Logger log = LoggerFactory.getLogger(Listener.class);
        private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
        private static final String SESSION_SERVER_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

        @Override
        public void handle(ServerboundEncryptionResponsePacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            var encryption = proxy.getEncryptionManager();

            // 1. RSA 解密 sharedSecret 和 verifyToken
            byte[] sharedSecret;
            byte[] decryptedToken;
            try {
                sharedSecret = encryption.decryptRSA(packet.getSharedSecret());
                decryptedToken = encryption.decryptRSA(packet.getVerifyToken());
            } catch (Exception e) {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.rsa_decrypt_failed"), e);
                disconnect(ctx, "Encryption error");
                return;
            }

            // 2. 校验 verifyToken
            if (!Arrays.equals(decryptedToken, context.getVerifyToken())) {
                log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.verify_token_mismatch"));
                disconnect(ctx, "Invalid verify token");
                return;
            }
            // 清除已使用的 token
            context.setVerifyToken(null);

            // 3. 安装 AES 加密 pipeline（必须在发任何后续包之前完成）
            try {
                ctx.pipeline().addBefore(
                        InternalConfig.HANDLER_DECODER,
                        InternalConfig.HANDLER_DECRYPT,
                        new EncryptionDecoder(encryption.createDecryptCipher(sharedSecret))
                );
                ctx.pipeline().addBefore(
                        InternalConfig.HANDLER_ENCODER,
                        InternalConfig.HANDLER_ENCRYPT,
                        new EncryptionEncoder(encryption.createEncryptCipher(sharedSecret))
                );
                log.debug("上游加密通道已启用");
            } catch (Exception e) {
                log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.pipeline_install_failed"), e);
                disconnect(ctx, "Encryption setup failed");
                return;
            }

            // 4. 根据 online-mode 决定是否请求 Mojang
            String username = context.getPendingUsername();
            context.setPendingUsername(null);

            if (!proxy.getConfig().isOnlineMode()) {
                // 仅加密，不验证：直接用离线 UUID 完成登录
                log.debug("加密通道已建立，跳过 Mojang 验证（离线模式）");
                GameProfile offlineProfile = new GameProfile(
                        username,
                        io.slidermc.starlight.utils.UUIDUtils.generateOfflineUuid(username),
                        java.util.List.of()
                );
                LoginHelper.completeLogin(ctx, proxy, offlineProfile);
                return;
            }

            String serverIdHash;
            try {
                serverIdHash = encryption.computeServerIdHash("", sharedSecret);
            } catch (Exception e) {
                log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.hash_compute_failed"), e);
                disconnect(ctx, "Encryption error");
                return;
            }

            String url = SESSION_SERVER_URL + "?username=" + username + "&serverId=" + serverIdHash;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> ctx.channel().eventLoop().execute(() -> {
                        if (response.statusCode() != 200) {
                            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.encryption.mojang_auth_failed"), response.statusCode(), username);
                            disconnect(ctx, "Failed to verify username!");
                            return;
                        }

                        // 5. 解析 Mojang 返回的 GameProfile
                        GameProfile profile;
                        try {
                            profile = parseMojangProfile(response.body());
                        } catch (Exception e) {
                            log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.mojang_response_parse_failed"), e);
                            disconnect(ctx, "Failed to parse session response");
                            return;
                        }

                        log.debug("Mojang 验证成功，玩家: {} ({})", profile.username(), profile.uuid());
                        // 6. 完成登录流程
                        LoginHelper.completeLogin(ctx, proxy, profile);
                    }))
                    .exceptionally(ex -> {
                        ctx.channel().eventLoop().execute(() -> {
                            log.error(proxy.getTranslateManager().translate("starlight.logging.error.encryption.session_server_request_failed"), ex);
                            disconnect(ctx, "Failed to contact authentication server");
                        });
                        return null;
                    });
        }

        private static GameProfile parseMojangProfile(String json) {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // UUID 格式：没有连字符的 32 位十六进制
            String rawId = obj.get("id").getAsString();
            UUID uuid = UUID.fromString(
                    rawId.substring(0, 8) + "-" +
                    rawId.substring(8, 12) + "-" +
                    rawId.substring(12, 16) + "-" +
                    rawId.substring(16, 20) + "-" +
                    rawId.substring(20)
            );
            String name = obj.get("name").getAsString();

            List<GameProfile.Property> properties = new ArrayList<>();
            if (obj.has("properties")) {
                JsonArray propsArray = obj.getAsJsonArray("properties");
                for (var element : propsArray) {
                    JsonObject prop = element.getAsJsonObject();
                    String propName = prop.get("name").getAsString();
                    String propValue = prop.get("value").getAsString();
                    String propSignature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                    properties.add(new GameProfile.Property(propName, propValue, propSignature));
                }
            }

            return new GameProfile(name, uuid, properties);
        }

        private static void disconnect(ChannelHandlerContext ctx, String reason) {
            ctx.channel().writeAndFlush(
                    new ClientboundDisconnectLoginPacket(Component.text(reason).color(NamedTextColor.RED))
            ).addListener(_ -> ctx.channel().close());
        }
    }
}
