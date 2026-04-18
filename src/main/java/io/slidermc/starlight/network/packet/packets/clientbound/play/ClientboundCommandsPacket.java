package io.slidermc.starlight.network.packet.packets.clientbound.play;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.command.ArgumentTypeData;
import io.slidermc.starlight.network.command.CommandNodeData;
import io.slidermc.starlight.network.packet.IMinecraftPacket;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ClientboundCommandsPacket implements IMinecraftPacket {
    private static final Logger log = LoggerFactory.getLogger(ClientboundCommandsPacket.class);
    private List<CommandNodeData> nodes;
    private int rootIndex;

    public ClientboundCommandsPacket() {
        this.nodes = new ArrayList<>();
        this.rootIndex = 0;
    }

    @Override
    public void decode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        int nodeCount = MinecraftCodecUtils.readVarInt(byteBuf);
        nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            CommandNodeData nodeData = new CommandNodeData();
            nodeData.read(byteBuf);
            nodes.add(nodeData);
        }
        rootIndex = MinecraftCodecUtils.readVarInt(byteBuf);
    }

    @Override
    public void encode(ByteBuf byteBuf, ProtocolVersion protocolVersion) {
        MinecraftCodecUtils.writeVarInt(byteBuf, nodes.size());
        for (CommandNodeData node : nodes) {
            node.write(byteBuf);
        }
        MinecraftCodecUtils.writeVarInt(byteBuf, rootIndex);
    }

    /**
     * 将代理自身的命令树合并进来（代理命令优先，覆盖同名后端命令）。
     */
    public void mergeProxyCommands(RootCommandNode<IStarlightCommandSource> proxyRoot) {
        if (nodes.isEmpty() || rootIndex < 0 || rootIndex >= nodes.size()) {
            log.warn("后端命令树为空或根节点索引无效，只使用代理命令");
            buildFromRoot(proxyRoot);
            return;
        }

        CommandNodeData backendRoot = nodes.get(rootIndex);

        for (CommandNode<IStarlightCommandSource> proxyChild : proxyRoot.getChildren()) {
            String commandName = proxyChild.getName();

            // 移除后端同名命令（代理优先）
            backendRoot.getChildren().removeIf(childIndex -> {
                if (childIndex < nodes.size()) {
                    CommandNodeData childNode = nodes.get(childIndex);
                    if (commandName.equals(childNode.getName())) {
                        log.debug("代理命令 '{}' 覆盖后端同名命令", commandName);
                        return true;
                    }
                }
                return false;
            });

            int newIndex = addNodeRecursively(proxyChild);
            backendRoot.getChildren().add(newIndex);
            log.debug("已添加代理命令: {}", commandName);
        }
    }

    /**
     * 从根节点重建整个节点列表（仅在后端树为空时使用）。
     */
    private void buildFromRoot(RootCommandNode<IStarlightCommandSource> rootNode) {
        nodes = new ArrayList<>();
        Map<CommandNode<IStarlightCommandSource>, Integer> nodeIndices = new LinkedHashMap<>();
        List<CommandNode<IStarlightCommandSource>> nodeList = new ArrayList<>();

        Queue<CommandNode<IStarlightCommandSource>> queue = new LinkedList<>();
        queue.add(rootNode);
        nodeIndices.put(rootNode, 0);
        nodeList.add(rootNode);

        while (!queue.isEmpty()) {
            CommandNode<IStarlightCommandSource> node = queue.poll();
            for (CommandNode<IStarlightCommandSource> child : node.getChildren()) {
                if (!nodeIndices.containsKey(child)) {
                    nodeIndices.put(child, nodeList.size());
                    nodeList.add(child);
                    queue.add(child);
                }
            }
        }

        for (CommandNode<IStarlightCommandSource> node : nodeList) {
            nodes.add(CommandNodeData.fromBrigadierNode(node, nodeIndices));
        }
        rootIndex = 0;
    }

    /**
     * 递归将代理命令节点追加到 nodes 列表，返回当前节点的索引。
     */
    private int addNodeRecursively(CommandNode<IStarlightCommandSource> node) {
        int currentIndex = nodes.size();
        CommandNodeData nodeData = new CommandNodeData();
        nodes.add(nodeData); // 先占位，后填充

        Map<CommandNode<IStarlightCommandSource>, Integer> childIndices = new LinkedHashMap<>();
        for (CommandNode<IStarlightCommandSource> child : node.getChildren()) {
            childIndices.put(child, addNodeRecursively(child));
        }

        fillNodeData(nodeData, node, childIndices);
        return currentIndex;
    }

    private void fillNodeData(CommandNodeData data,
                               CommandNode<IStarlightCommandSource> node,
                               Map<CommandNode<IStarlightCommandSource>, Integer> childIndices) {
        byte flags = 0;
        if (node instanceof LiteralCommandNode)       flags |= CommandNodeData.NODE_TYPE_LITERAL;
        else if (node instanceof ArgumentCommandNode) flags |= CommandNodeData.NODE_TYPE_ARGUMENT;
        // NODE_TYPE_ROOT = 0，无需 OR 操作

        if (node.getCommand() != null)  flags |= CommandNodeData.FLAG_EXECUTABLE;
        if (node.getRedirect() != null) flags |= CommandNodeData.FLAG_HAS_REDIRECT;

        List<Integer> children = new ArrayList<>(childIndices.values());
        data.setChildren(children);

        if (node instanceof LiteralCommandNode<?> lit) {
            data.setName(lit.getLiteral());
        } else if (node instanceof ArgumentCommandNode<?, ?> arg) {
            data.setName(arg.getName());
            data.setArgumentType(ArgumentTypeData.fromBrigadierType(arg.getType()));
            if (arg.getCustomSuggestions() != null) {
                flags |= CommandNodeData.FLAG_HAS_SUGGESTIONS;
                data.setSuggestionsType("minecraft:ask_server");
            }
        }

        data.setFlags(flags);
    }

    public List<CommandNodeData> getNodes() { return nodes; }
    public int getRootIndex() { return rootIndex; }

    // -------------------------------------------------------------------------

    public static class Listener implements IPacketListener<ClientboundCommandsPacket> {
        @Override
        public void handle(ClientboundCommandsPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            // 将代理命令树合并进后端命令树
            RootCommandNode<IStarlightCommandSource> proxyRoot =
                    proxy.getCommandDispatcher().getRoot();
            packet.mergeProxyCommands(proxyRoot);

            // 转发给上游客户端
            io.slidermc.starlight.network.client.StarlightMinecraftClient client =
                    ctx.channel().attr(io.slidermc.starlight.network.context.AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT)
                       .get().getClient();
            client.getPlayerChannel().writeAndFlush(packet);
        }
    }
}
