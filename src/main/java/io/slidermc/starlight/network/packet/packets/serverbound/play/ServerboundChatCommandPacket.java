package io.slidermc.starlight.network.packet.packets.serverbound.play;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.tree.CommandNode;

import java.util.concurrent.CompletableFuture;

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
            String normalizedCommand = packet.command.trim();
            if (normalizedCommand.isEmpty()) {
                context.getDownstreamChannel().writeAndFlush(packet);
                return;
            }
            String commandName = normalizedCommand.split(" ")[0];
            if (proxy.getCommandManager().hasCommand(commandName)) {
                CommandNode<IStarlightCommandSource> node = proxy.getCommandManager().getDispatcher().getRoot().getChild(commandName);
                // 玩家用不了就直接转发到下游, 防止泄露
                if (node != null && !node.canUse(context.getPlayer())) {
                    context.getDownstreamChannel().writeAndFlush(packet);
                    return;
                }
                if (proxy.getConfig().isLoggingCommand()) {
                    String logCommand = packet.command;
                    if (logCommand != null && logCommand.length() > 256) {
                        logCommand = StringUtils.truncateByCharCount(logCommand, 256) + "...";
                    }
                    log.info(proxy.getTranslateManager().translate("starlight.logging.info.player_executed_command"), context.getPlayer().getGameProfile().username(), "/" + logCommand);
                }
                final String finalCommand = normalizedCommand;
                CompletableFuture.runAsync(
                        () -> proxy.getCommandManager().execute(finalCommand, context.getPlayer()),
                        proxy.getExecutors().getCommandExecutor()
                ).exceptionally(throwable -> {
                            log.error(proxy.getTranslateManager().translate("starlight.logging.error.error_on_executing_command"), finalCommand, throwable);
                            return null;
                        });
                return;
            }

            // 不是代理命令，透明转发给后端
            context.getDownstreamChannel().writeAndFlush(packet);
        }
    }
}
