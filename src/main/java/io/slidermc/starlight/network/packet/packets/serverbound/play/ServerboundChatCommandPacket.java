package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerboundChatCommandPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ServerboundChatCommandPacket.class);
    private String command;

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeString(byteBuf, command);
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        this.command = MinecraftCodecUtils.readString(byteBuf);
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public static class Listener implements IPacketListener<ServerboundChatCommandPacket> {
        @Override
        public void handle(ServerboundChatCommandPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            ConnectionContext context = ctx.channel().attr(AttributeKeys.CONNECTION_CONTEXT).get();

            // 检查是否是代理命令
            // ServerboundChatCommand 中 command 不含 '/'，但做防御性处理
            String normalizedCommand = packet.command.trim();
            if (normalizedCommand.startsWith("/")) {
                normalizedCommand = normalizedCommand.substring(1).trim();
            }
            if (normalizedCommand.isEmpty()) {
                context.getDownstreamChannel().writeAndFlush(packet);
                return;
            }
            String commandName = normalizedCommand.split(" ")[0];
            if (proxy.getCommandManager().hasCommand(commandName)) {
                if (proxy.getConfig().isLoggingCommand())
                    log.info(proxy.getTranslateManager().translate("starlight.logging.info.player_executed_command"), context.getPlayer().getGameProfile().username(), "/" + packet.command);
                proxy.getCommandManager().execute(normalizedCommand, context.getPlayer());
                return;
            }

            // 不是代理命令，透明转发给后端
            context.getDownstreamChannel().writeAndFlush(packet);
        }
    }
}
