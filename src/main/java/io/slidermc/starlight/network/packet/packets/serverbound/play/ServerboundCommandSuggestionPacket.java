package io.slidermc.starlight.network.packet.packets.serverbound.play;

import com.mojang.brigadier.ParseResults;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.packet.packets.clientbound.play.ClientboundCommandSuggestionsPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command Suggestions Request 数据包（serverbound play）
 * 客户端请求命令 Tab 补全。
 */
public class ServerboundCommandSuggestionPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundCommandSuggestionPacket.class);

    private int transactionId;
    private String text;

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.transactionId = MinecraftCodecUtils.readVarInt(byteBuf);
        this.text          = MinecraftCodecUtils.readString(byteBuf);
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, transactionId);
        MinecraftCodecUtils.writeString(byteBuf, text);
    }

    public int getTransactionId() { return transactionId; }
    public String getText() { return text; }

    // -------------------------------------------------------------------------

    public static class Listener implements IPacketListener<ServerboundCommandSuggestionPacket> {
        @Override
        public void handle(ServerboundCommandSuggestionPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext connCtx = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();
            ProxiedPlayer player = connCtx.getPlayer();

            // text 包含 '/'，Brigadier 命令字符串不含 '/'
            int offset = packet.text.startsWith("/") ? 1 : 0;
            String commandText = packet.text.substring(offset).trim();

            if (commandText.isEmpty()) {
                connCtx.getDownstreamChannel().writeAndFlush(packet);
                return;
            }

            String commandName = commandText.split(" ")[0];

            // 检查代理是否注册了该命令的根节点
            if (proxy.getCommandDispatcher().getRoot().getChild(commandName) != null) {
                // 由代理处理 Tab 补全
                ParseResults<IStarlightCommandSource> parse =
                        proxy.getCommandDispatcher().parse(commandText, player);


                proxy.getCommandDispatcher().getCompletionSuggestions(parse)
                        .thenAccept(suggestions -> {
                            int start  = suggestions.getRange().getStart() + offset;
                            int length = suggestions.getRange().getLength();

                            ClientboundCommandSuggestionsPacket response =
                                    new ClientboundCommandSuggestionsPacket(packet.transactionId, start, length);

                            suggestions.getList().forEach(s -> response.addSuggestion(s.getText()));

                            player.getChannel().writeAndFlush(response);
                            log.debug("已发送补全列表给玩家: {}, 命令: {}, 列表长度: {}",
                                    player.getGameProfile().username(),
                                    commandText,
                                    suggestions.getList().size());
                        })
                        .exceptionally(throwable -> {
                            log.error(proxy.getTranslateManager().translate("starlight.logging.error.command_suggent_failed"),
                                    player.getGameProfile().username(), commandText, throwable);
                            // 返回空建议列表，避免客户端请求悬挂
                            ClientboundCommandSuggestionsPacket empty =
                                    new ClientboundCommandSuggestionsPacket(packet.transactionId, offset, 0);
                            player.getChannel().writeAndFlush(empty);
                            return null;
                        });

                // 不转发给后端
                return;
            }

            // 不是代理命令，透明转发给后端
            connCtx.getDownstreamChannel().writeAndFlush(packet);
        }
    }
}

