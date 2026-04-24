package io.slidermc.starlight.network.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.api.command.source.IStarlightCommandSource;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令节点数据
 * 表示命令树中的一个节点
 */
public class CommandNodeData {
    // 节点类型标志
    public static final byte NODE_TYPE_ROOT = 0;
    public static final byte NODE_TYPE_LITERAL = 1;
    public static final byte NODE_TYPE_ARGUMENT = 2;
    
    // 其他标志
    public static final byte FLAG_EXECUTABLE = 0x04;
    public static final byte FLAG_HAS_REDIRECT = 0x08;
    public static final byte FLAG_HAS_SUGGESTIONS = 0x10;
    public static final byte FLAG_IS_RESTRICTED = 0x20; // 保留
    
    private byte flags;
    private List<Integer> children;
    private Integer redirectNode;
    private String name;
    private ArgumentTypeData argumentType;
    private String suggestionsType;
    
    public CommandNodeData() {
        this.children = new ArrayList<>();
    }

    public CommandNodeData(CommandNodeData other) {
        this.flags = other.flags;
        this.children = new ArrayList<>(other.children);
        this.redirectNode = other.redirectNode;
        this.name = other.name;
        this.suggestionsType = other.suggestionsType;
        this.argumentType = other.argumentType != null ? new ArgumentTypeData(other.argumentType) : null;
    }
    
    /**
     * 从Brigadier命令节点创建CommandNodeData
     */
    public static CommandNodeData fromBrigadierNode(
            CommandNode<IStarlightCommandSource> node,
            Map<CommandNode<IStarlightCommandSource>, Integer> nodeIndices) {
        
        CommandNodeData data = new CommandNodeData();
        
        // 设置节点类型
        byte flags = 0;
        if (node instanceof RootCommandNode) {
            flags |= NODE_TYPE_ROOT;
        } else if (node instanceof LiteralCommandNode) {
            flags |= NODE_TYPE_LITERAL;
        } else if (node instanceof ArgumentCommandNode) {
            flags |= NODE_TYPE_ARGUMENT;
        }
        
        // 设置可执行标志
        if (node.getCommand() != null) {
            flags |= FLAG_EXECUTABLE;
        }
        
        // 设置重定向标志
        if (node.getRedirect() != null) {
            flags |= FLAG_HAS_REDIRECT;
        }
        
        // FLAG_IS_RESTRICTED 仅在节点确实需要权限检查时设置。
        // Brigadier 的 getRequirement() 始终非 null（默认为 s -> true），
        // 因此不能用 != null 判断；暂不设置此标志，避免将所有节点标记为受限。

        data.flags = flags;
        
        // 添加子节点索引
        for (CommandNode<IStarlightCommandSource> child : node.getChildren()) {
            Integer childIndex = nodeIndices.get(child);
            if (childIndex != null) {
                data.children.add(childIndex);
            }
        }
        
        // 设置重定向节点
        if (node.getRedirect() != null) {
            data.redirectNode = nodeIndices.get(node.getRedirect());
        }
        
        // 设置节点名称
        if (node instanceof LiteralCommandNode) {
            data.name = ((LiteralCommandNode<?>) node).getLiteral();
        } else if (node instanceof ArgumentCommandNode<?, ?> argNode) {
            data.name = argNode.getName();
            
            // 设置参数类型
            data.argumentType = ArgumentTypeData.fromBrigadierType(argNode.getType());
            
            // 设置建议类型
            if (argNode.getCustomSuggestions() != null) {
                flags |= FLAG_HAS_SUGGESTIONS;
                data.flags = flags;
                data.suggestionsType = "minecraft:ask_server";
            }
        }
        
        return data;
    }
    
    public void read(ByteBuf buf) {
        flags = buf.readByte();
        
        // 读取子节点
        int childCount = MinecraftCodecUtils.readVarInt(buf);
        if (childCount < 0) {
            throw new IllegalArgumentException("Invalid child count: " + childCount + ", flags: " + flags);
        }
        children = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) {
            children.add(MinecraftCodecUtils.readVarInt(buf));
        }
        
        // 读取重定向节点
        if ((flags & FLAG_HAS_REDIRECT) != 0) {
            redirectNode = MinecraftCodecUtils.readVarInt(buf);
        }
        
        // 读取节点名称
        byte nodeType = (byte) (flags & 0x03);
        if (nodeType == NODE_TYPE_LITERAL || nodeType == NODE_TYPE_ARGUMENT) {
            name = MinecraftCodecUtils.readString(buf);
        }
        
        // 读取参数类型
        if (nodeType == NODE_TYPE_ARGUMENT) {
            argumentType = new ArgumentTypeData();
            argumentType.read(buf);
        }
        
        // 读取建议类型
        if ((flags & FLAG_HAS_SUGGESTIONS) != 0) {
            suggestionsType = MinecraftCodecUtils.readString(buf);
        }
    }
    
    public void write(ByteBuf buf) {
        buf.writeByte(flags);
        
        // 写入子节点
        MinecraftCodecUtils.writeVarInt(buf, children.size());
        for (Integer child : children) {
            MinecraftCodecUtils.writeVarInt(buf, child);
        }
        
        // 写入重定向节点
        if ((flags & FLAG_HAS_REDIRECT) != 0 && redirectNode != null) {
            MinecraftCodecUtils.writeVarInt(buf, redirectNode);
        }
        
        // 写入节点名称
        byte nodeType = (byte) (flags & 0x03);
        if (nodeType == NODE_TYPE_LITERAL || nodeType == NODE_TYPE_ARGUMENT) {
            MinecraftCodecUtils.writeString(buf, name != null ? name : "");
        }
        
        // 写入参数类型
        if (nodeType == NODE_TYPE_ARGUMENT && argumentType != null) {
            argumentType.write(buf);
        }
        
        // 写入建议类型
        if ((flags & FLAG_HAS_SUGGESTIONS) != 0 && suggestionsType != null) {
            MinecraftCodecUtils.writeString(buf, suggestionsType);
        }
    }

    public byte getFlags() {
        return flags;
    }

    public void setFlags(byte flags) {
        this.flags = flags;
    }

    public List<Integer> getChildren() {
        return children;
    }

    public void setChildren(List<Integer> children) {
        this.children = children;
    }

    public Integer getRedirectNode() {
        return redirectNode;
    }

    public void setRedirectNode(Integer redirectNode) {
        this.redirectNode = redirectNode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArgumentTypeData getArgumentType() {
        return argumentType;
    }

    public void setArgumentType(ArgumentTypeData argumentType) {
        this.argumentType = argumentType;
    }

    public String getSuggestionsType() {
        return suggestionsType;
    }

    public void setSuggestionsType(String suggestionsType) {
        this.suggestionsType = suggestionsType;
    }
}
