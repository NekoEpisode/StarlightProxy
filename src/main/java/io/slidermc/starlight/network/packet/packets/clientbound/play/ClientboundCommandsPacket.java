package io.slidermc.starlight.network.packet.packets.clientbound.play;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.command.ArgumentTypeData;
import io.slidermc.starlight.network.command.CommandNodeData;
import io.slidermc.starlight.network.context.AttributeKeys;
import io.slidermc.starlight.network.context.ConnectionContext;
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

    /** 在 mergeProxyCommands 期间共享，避免污染内部方法签名 */
    private transient TranslateManager translateManager;

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
     *
     * @param source 命令来源，用于权限过滤（玩家或控制台）
     */
    public void mergeProxyCommands(RootCommandNode<IStarlightCommandSource> proxyRoot, TranslateManager translateManager, IStarlightCommandSource source) {
        this.translateManager = translateManager;
        try {
            if (nodes.isEmpty() || rootIndex < 0 || rootIndex >= nodes.size()) {
                log.warn(translateManager.translate("starlight.logging.warn.downstream_command_tree_empty_or_root_index_invaild"));
                buildFromRoot(proxyRoot, source);
                return;
            }

            CommandNodeData backendRoot = nodes.get(rootIndex);
            Map<CommandNode<IStarlightCommandSource>, Integer> allNodeIndices = new LinkedHashMap<>();

            // Pass 1: DFS collect all proxy nodes, append placeholder CommandNodeData
            for (CommandNode<IStarlightCommandSource> proxyChild : proxyRoot.getChildren()) {
                if (!proxyChild.canUse(source)) continue;
                String commandName = proxyChild.getName();

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

                collectProxyNodes(proxyChild, source, allNodeIndices);
            }

            // Pass 2: fill all collected proxy node data (redirects now resolvable)
            for (Map.Entry<CommandNode<IStarlightCommandSource>, Integer> entry : allNodeIndices.entrySet()) {
                CommandNode<IStarlightCommandSource> node = entry.getKey();
                int index = entry.getValue();
                CommandNodeData data = nodes.get(index);

                Map<CommandNode<IStarlightCommandSource>, Integer> childIndices = new LinkedHashMap<>();
                for (CommandNode<IStarlightCommandSource> child : node.getChildren()) {
                    if (!child.canUse(source)) continue;
                    Integer ci = allNodeIndices.get(child);
                    if (ci != null) childIndices.put(child, ci);
                }

                fillNodeData(data, node, childIndices, allNodeIndices);
            }

            // Add root child references
            for (CommandNode<IStarlightCommandSource> proxyChild : proxyRoot.getChildren()) {
                if (!proxyChild.canUse(source)) continue;
                Integer idx = allNodeIndices.get(proxyChild);
                if (idx != null) backendRoot.getChildren().add(idx);
            }
        } finally {
            this.translateManager = null;
        }
    }

    /**
     * 从根节点重建整个节点列表（仅在后端树为空时使用）。
     */
    private void buildFromRoot(RootCommandNode<IStarlightCommandSource> rootNode, IStarlightCommandSource source) {
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
                if (!child.canUse(source)) continue;
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
     * DFS 收集代理节点并分配索引，向 nodes 列表追加占位 CommandNodeData。
     */
    private void collectProxyNodes(CommandNode<IStarlightCommandSource> node, IStarlightCommandSource source,
                                   Map<CommandNode<IStarlightCommandSource>, Integer> allNodeIndices) {
        int currentIndex = nodes.size();
        allNodeIndices.put(node, currentIndex);
        nodes.add(new CommandNodeData());

        for (CommandNode<IStarlightCommandSource> child : node.getChildren()) {
            if (!child.canUse(source)) continue;
            if (!allNodeIndices.containsKey(child)) {
                collectProxyNodes(child, source, allNodeIndices);
            }
        }
    }

    private void fillNodeData(CommandNodeData data,
                               CommandNode<IStarlightCommandSource> node,
                               Map<CommandNode<IStarlightCommandSource>, Integer> childIndices,
                               Map<CommandNode<IStarlightCommandSource>, Integer> allNodeIndices) {
        byte flags = 0;
        if (node instanceof LiteralCommandNode)       flags |= CommandNodeData.NODE_TYPE_LITERAL;
        else if (node instanceof ArgumentCommandNode) flags |= CommandNodeData.NODE_TYPE_ARGUMENT;

        if (node.getCommand() != null)  flags |= CommandNodeData.FLAG_EXECUTABLE;
        if (node.getRedirect() != null) {
            Integer redirectIndex = allNodeIndices.get(node.getRedirect());
            if (redirectIndex != null) {
                flags |= CommandNodeData.FLAG_HAS_REDIRECT;
                data.setRedirectNode(redirectIndex);
            } else {
                log.warn(translateManager.translate("starlight.logging.warn.cannot_found_target_redirect_node"), node.getName());
            }
        }

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

    /**
     * 从缓存的命令树数据恢复此包，用于权限更新后重建。
     */
    public void loadFromCache(List<CommandNodeData> cachedNodes, int cachedRootIndex) {
        this.nodes = new ArrayList<>(cachedNodes.size());
        for (CommandNodeData node : cachedNodes) {
            this.nodes.add(new CommandNodeData(node));
        }
        this.rootIndex = cachedRootIndex;
    }

    // -------------------------------------------------------------------------

    public static class Listener implements IPacketListener<ClientboundCommandsPacket> {
        @Override
        public void handle(ClientboundCommandsPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
            RootCommandNode<IStarlightCommandSource> proxyRoot = proxy.getCommandDispatcher().getRoot();
            ConnectionContext context = ctx.channel().attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT).get().getClient().getPlayerChannel().attr(AttributeKeys.CONNECTION_CONTEXT).get();

            context.cacheCommandTree(packet.getNodes(), packet.getRootIndex());

            packet.mergeProxyCommands(proxyRoot, proxy.getTranslateManager(), context.getPlayer());

            StarlightMinecraftClient client = ctx.channel()
                    .attr(AttributeKeys.DOWNSTREAM_CONNECTION_CONTEXT)
                    .get().getClient();
            client.getPlayerChannel().writeAndFlush(packet);
        }
    }
}
