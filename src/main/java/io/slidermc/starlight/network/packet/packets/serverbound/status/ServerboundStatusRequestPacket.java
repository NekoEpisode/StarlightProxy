package io.slidermc.starlight.network.packet.packets.serverbound.status;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.event.events.internal.ProxyPingEvent;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.config.InternalConfig;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.MiniMessageUtils;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ServerboundStatusRequestPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundStatusRequestPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ServerboundStatusRequestPacket> {
        @Override
        public void handle(ServerboundStatusRequestPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            log.debug("收到StatusRequest包");
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            ProtocolVersion protocolVersion = context.getHandshakeInformation().getProtocolVersion();
            int versionProtocol = -1;
            if (protocolVersion != ProtocolVersion.UNKNOWN) {
                versionProtocol = protocolVersion.getProtocolVersionCode();
            } else {
                log.debug("不支持的版本: {}", context.getHandshakeInformation().getOriginalProtocolVersion());
            }
            List<ClientboundStatusResponsePacket.SamplePlayer> samplePlayers = new ArrayList<>();
            for (ProxiedPlayer player : proxy.getPlayerManager().getPlayers()) {
                samplePlayers.add(
                        new ClientboundStatusResponsePacket.SamplePlayer(
                                player.getGameProfile().username(), player.getGameProfile().uuid()
                        )
                );
            }

            int origMaxPlayers = proxy.getConfig().getMaxPlayers();
            int origOnlinePlayers = samplePlayers.size();
            Component origDescription = MiniMessageUtils.MINI_MESSAGE.deserialize(proxy.getConfig().getMotd());
            String origFavicon = proxy.getFaviconBase64();

            ClientboundStatusResponsePacket originalResponse = new ClientboundStatusResponsePacket(
                    InternalConfig.VERSION_STRING, versionProtocol,
                    origMaxPlayers, origOnlinePlayers,
                    new ArrayList<>(samplePlayers), origDescription, origFavicon, false
            );

            ProxyPingEvent event = new ProxyPingEvent(
                    InternalConfig.VERSION_STRING, versionProtocol,
                    origMaxPlayers, origOnlinePlayers,
                    samplePlayers, origDescription, origFavicon, false
            );

            proxy.getEventManager().fireAsync(event, proxy.getExecutors().getEventExecutor())
                    .whenComplete((e, throwable) -> {
                        if (throwable != null) {
                            log.warn("ProxyPingEvent failed, using original response", throwable);
                            ctx.channel().writeAndFlush(originalResponse);
                            return;
                        }
                        if (e.isCancelled()) {
                            return;
                        }
                        ctx.channel().writeAndFlush(new ClientboundStatusResponsePacket(
                                e.getVersionName(), e.getVersionProtocol(),
                                e.getMaxPlayers(), e.getOnlinePlayers(),
                                e.getSamplePlayers(), e.getDescription(),
                                e.getFavicon(), e.isEnforcesSecureChat()
                        ));
                    });
        }
    }
}
