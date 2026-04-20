package io.slidermc.starlight.utils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundDisconnectPlayPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一处理"向上游玩家转发断开包并关闭双侧 channel"的工具类。
 */
public final class DisconnectUtils {
    private static final Logger log = LoggerFactory.getLogger(DisconnectUtils.class);

    private DisconnectUtils() {}

    /**
     * 向上游玩家发送断开包并在写出完成后关闭玩家 channel。
     *
     * <p>根据玩家当前的 {@code outboundState} 自动选择正确的包类型：
     * <ul>
     *   <li>LOGIN         → {@link ClientboundDisconnectLoginPacket}</li>
     *   <li>CONFIGURATION → {@link ClientboundDisconnectConfigurationPacket}</li>
     *   <li>PLAY          → {@link ClientboundDisconnectPlayPacket}</li>
     * </ul>
     *
     * <p>若 playerChannel 为 null 或已关闭，则直接返回不做任何操作。
     *
     * @param client 下游客户端（持有 playerChannel 引用）
     * @param reason 断开原因
     */
    public static void forwardAndClose(StarlightMinecraftClient client, Component reason, StarlightProxy proxy) {
        Channel playerChannel = client.getPlayerChannel();
        if (playerChannel == null || !playerChannel.isActive()) {
            return;
        }

        ConnectionContext context = playerChannel.attr(AttributeKeys.CONNECTION_CONTEXT).get();
        ProtocolState state = context.getOutboundState();

        IMinecraftPacket packet = switch (state) {
            case LOGIN         -> new ClientboundDisconnectLoginPacket(reason);
            case CONFIGURATION -> new ClientboundDisconnectConfigurationPacket(reason);
            case PLAY          -> new ClientboundDisconnectPlayPacket(reason);
            default            -> null;
        };

        String reasonText = PlainTextComponentSerializer.plainText().serialize(reason);
        log.debug("向玩家转发断开包 [{}]: {}", state, reasonText);

        if (packet != null) {
            // 写完后自动关闭 channel，确保玩家收到断开原因后连接才断
            playerChannel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
        } else {
            log.warn(proxy.getTranslateManager().translate("starlight.logging.warn.disconnect.type_failed"), state);
            playerChannel.close();
        }
    }
}

