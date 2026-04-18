package io.slidermc.starlight.network.packet.packets.serverbound.login;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.network.codec.CompressionDecoder;
import io.slidermc.starlight.network.codec.CompressionEncoder;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundLoginSuccessPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundSetCompressionPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ServerboundLoginStartPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundLoginStartPacket.class);

    private String username;
    private UUID uuid;

    public ServerboundLoginStartPacket() {}

    public ServerboundLoginStartPacket(String username, UUID uuid) {
        this.username = username;
        this.uuid = uuid;
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, this.username);
        MinecraftCodecUtils.writeUUID(byteBuf, this.uuid);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.username = MinecraftCodecUtils.readString(byteBuf);
        this.uuid = MinecraftCodecUtils.readUUID(byteBuf);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public static class Listener implements IPacketListener<ServerboundLoginStartPacket> {
        @Override
        public void handle(ServerboundLoginStartPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            if (context.getHandshakeInformation().getProtocolVersion() == ProtocolVersion.UNKNOWN) {
                log.debug("不支持的版本，踢出");
                ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(Component.text("Unsupported protocol version: " + context.getHandshakeInformation().getOriginalProtocolVersion()).color(NamedTextColor.RED))).addListener(_ -> {
                    ctx.channel().close();
                });
                return;
            }

            if (proxy.getConfig().isOnlineMode()) {
                // TODO: 实现正版验证
                log.error("Online mode is not implemented yet");
                ctx.channel().writeAndFlush(new ClientboundDisconnectLoginPacket(Component.text("Online mode is not implemented yet").color(NamedTextColor.RED))).addListener(_ -> {
                    ctx.channel().close();
                });
                return;
            }

            log.debug("玩家 {} 以离线模式登录", packet.getUsername());
            ProxiedPlayer player = new ProxiedPlayer(new GameProfile(packet.username, packet.uuid, List.of()), ctx.channel(), proxy);
            log.debug("已创建ProxiedPlayer对象: {}", player);
            player.getConnectionContext().setPlayer(player);
            proxy.getPlayerManager().addPlayer(player);

            int threshold = proxy.getConfig().getCompressThreshold();
            if (threshold >= 0) {
                // 等 SetCompression 写出完成后再安装 pipeline 并发 LoginSuccess，
                // 否则 LoginSuccess 可能在压缩生效前就发出，导致客户端解析失败。
                ctx.channel().writeAndFlush(new ClientboundSetCompressionPacket(threshold)).addListener(_ -> {
                    ctx.pipeline().addBefore("decoder", "decompress", new CompressionDecoder());
                    ctx.pipeline().addBefore("encoder", "compress", new CompressionEncoder(threshold));
                    log.debug("上游已启用压缩，阈值: {}", threshold);
                    sendLoginSuccess(ctx, player);
                });
            } else {
                sendLoginSuccess(ctx, player);
            }
        }

        private static void sendLoginSuccess(ChannelHandlerContext ctx, ProxiedPlayer player) {
            ctx.channel().writeAndFlush(new ClientboundLoginSuccessPacket(player.getGameProfile())).addListener(_ -> {
                player.getConnectionContext().setOutboundState(ProtocolState.CONFIGURATION);
                log.debug("上游Outbound切换到CONFIGURATION");
            });
        }
    }
}
