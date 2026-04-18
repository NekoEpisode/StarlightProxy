package io.slidermc.starlight.network.packet.packets.clientbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.codec.utils.NBTHelper;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command Suggestions Response 数据包（clientbound play）
 * 服务器响应命令 Tab 补全请求。
 */
public class ClientboundCommandSuggestionsPacket implements IMinecraftPacket {
    private int transactionId;
    private int start;
    private int length;
    private List<Suggestion> suggestions;

    public ClientboundCommandSuggestionsPacket() {
        this.suggestions = new ArrayList<>();
    }

    public ClientboundCommandSuggestionsPacket(int transactionId, int start, int length) {
        this.transactionId = transactionId;
        this.start = start;
        this.length = length;
        this.suggestions = new ArrayList<>();
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.transactionId = MinecraftCodecUtils.readVarInt(byteBuf);
        this.start          = MinecraftCodecUtils.readVarInt(byteBuf);
        this.length         = MinecraftCodecUtils.readVarInt(byteBuf);

        int count = MinecraftCodecUtils.readVarInt(byteBuf);
        this.suggestions = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String match = MinecraftCodecUtils.readString(byteBuf);
            Component tooltip = null;

            if (byteBuf.readBoolean()) {
                // 1.21.5+ tooltip 以 NBT 格式传输
                tooltip = NBTHelper.readComponent(byteBuf);
            }

            suggestions.add(new Suggestion(match, tooltip));
        }
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, transactionId);
        MinecraftCodecUtils.writeVarInt(byteBuf, start);
        MinecraftCodecUtils.writeVarInt(byteBuf, length);

        MinecraftCodecUtils.writeVarInt(byteBuf, suggestions.size());

        for (Suggestion suggestion : suggestions) {
            MinecraftCodecUtils.writeString(byteBuf, suggestion.getMatch());

            if (suggestion.getTooltip() != null) {
                byteBuf.writeBoolean(true);
                NBTHelper.writeComponent(byteBuf, suggestion.getTooltip());
            } else {
                byteBuf.writeBoolean(false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 便捷方法

    public void addSuggestion(String match) {
        suggestions.add(new Suggestion(match, null));
    }

    public void addSuggestion(String match, Component tooltip) {
        suggestions.add(new Suggestion(match, tooltip));
    }

    // -------------------------------------------------------------------------
    // Getters / Setters

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    // -------------------------------------------------------------------------

    public static class Listener implements IPacketListener<ClientboundCommandSuggestionsPacket> {
        @Override
        public void handle(ClientboundCommandSuggestionsPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // 透明转发
            DownstreamConnectionContext downContext = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get();
            StarlightMinecraftClient client = downContext.getClient();
            client.getPlayerChannel().writeAndFlush(packet);
        }
    }

    // -------------------------------------------------------------------------

    public static class Suggestion {
        private final String match;
        private final Component tooltip;

        public Suggestion(String match, @Nullable Component tooltip) {
            this.match = match;
            this.tooltip = tooltip;
        }

        public String getMatch() { return match; }

        @Nullable
        public Component getTooltip() { return tooltip; }
    }
}

