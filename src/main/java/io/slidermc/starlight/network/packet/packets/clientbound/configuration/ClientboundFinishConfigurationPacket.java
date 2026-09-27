package io.slidermc.starlight.network.packet.packets.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.channel.ChannelPayload;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.serverbound.configuration.ServerboundPluginMessageConfigurationPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

public class ClientboundFinishConfigurationPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundFinishConfigurationPacket.class);

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {}

    public static class Listener implements IPacketListener<ClientboundFinishConfigurationPacket> {
        @Override
        public void handle(ClientboundFinishConfigurationPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            DownstreamConnectionContext context = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            StarlightMinecraftClient client = context.getClient();
            client.setInboundState(ProtocolState.PLAY);
            log.debug("下游Inbound设置为PLAY [1]");

            // 必须早于 finish_configuration 写出：Paper 处理该包时会把当前已知的插件消息通道写入
            // 连接 cookie，此后新增的通道对本次连接不再生效。
            announceChannels(ctx, client, proxy);

            client.getPlayerChannel().writeAndFlush(packet).addListener(_ -> {
                ConnectionContext context1 = client.getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
                context1.setOutboundState(ProtocolState.PLAY);
                log.debug("上游Outbound设置为PLAY [2]");
            });
        }

        /**
         * 把「代理注册的通道 ∪ 客户端声明的通道」中尚未下发的部分声明给下游服务器。
         *
         * <p>下游的 {@code CraftPlayer#sendPluginMessage} 只允许发送客户端已声明的通道，而原版客户端
         * 不会处理代理发出的 register 包，因此通道声明必须由代理直接下发给下游。
         *
         * <p>该包在下游每次进入 PLAY 前都会到达，因此服务器切换后的新下游同样会在这里收到全量声明
         * （{@code setDownstreamChannel} 会清空已下发状态）；而插件在玩家游戏期间新注册的通道，
         * 也会在最近一次时机作为差集补发出去。
         */
        private void announceChannels(ChannelHandlerContext ctx, StarlightMinecraftClient client,
                                     StarlightProxy proxy) {
            ConnectionContext playerContext =
                    client.getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            if (playerContext == null) {
                return;
            }

            Set<Key> channels = new LinkedHashSet<>(proxy.getChannelRegistry().getChannels());
            channels.addAll(playerContext.getClientChannels());

            Set<Key> pending = playerContext.diffAnnounced(channels);
            if (pending.isEmpty()) {
                return;
            }

            playerContext.setAnnouncedChannels(channels);

            try {
                ctx.channel().writeAndFlush(new ServerboundPluginMessageConfigurationPacket(
                        ChannelPayload.REGISTER_CHANNEL, ChannelPayload.write(pending)));
                log.debug("已向 {} 声明 {} 个插件消息通道", client.getAddress(), pending.size());
            } catch (Exception e) {
                log.error(proxy.getTranslateManager().translate(
                        "starlight.logging.error.channel.announce_failed"), pending.size(), e);
            }
        }
    }
}
