package io.slidermc.starlight.network.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;

/**
 * 命令参数类型数据
 *
 * <p>表示命令树中一个参数节点的类型及其属性。类型本身以语义 {@link ArgumentType} 保存，
 * 读写时通过 {@link CommandArgumentTypeRegistry} 按协议版本换算线上参数类型 ID，
 * 因此同一份命令树数据可以在 1.21.11 到 26.3 之间正确编解码。
 *
 * <p>注意：{@link ArgumentType} 的语义 ID 与线上 ID 并不相同。例如 {@code dialog} 在协议 775/776
 * 中的线上 ID 是 55，在 26.3（协议 777）中是 58，但语义 ID 始终是同一个。
 */
public class ArgumentTypeData {

    private final CommandArgumentTypeRegistry registry;

    /** 参数类型，语义类型而非线上 ID */
    private ArgumentType type;

    // 属性字段，仅在 type.bracketed() 为 true 时有效
    private byte numberFlags;
    private float minFloat, maxFloat;
    private double minDouble, maxDouble;
    private int minInt, maxInt;
    private long minLong, maxLong;
    private int stringBehavior;
    private byte entityFlags;
    private byte scoreHolderFlags;
    private int minTime;
    private String registryKey;

    /**
     * @param registry 命令参数类型注册表，用于按协议版本换算线上 ID
     */
    public ArgumentTypeData(CommandArgumentTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * 拷贝构造。
     *
     * @param other 被拷贝的数据
     */
    public ArgumentTypeData(ArgumentTypeData other) {
        this.registry = other.registry;
        this.type = other.type;
        this.numberFlags = other.numberFlags;
        this.minFloat = other.minFloat;
        this.maxFloat = other.maxFloat;
        this.minDouble = other.minDouble;
        this.maxDouble = other.maxDouble;
        this.minInt = other.minInt;
        this.maxInt = other.maxInt;
        this.minLong = other.minLong;
        this.maxLong = other.maxLong;
        this.stringBehavior = other.stringBehavior;
        this.entityFlags = other.entityFlags;
        this.scoreHolderFlags = other.scoreHolderFlags;
        this.minTime = other.minTime;
        this.registryKey = other.registryKey;
    }

    /**
     * 从 Brigadier 参数类型创建参数类型数据。
     *
     * <p>用于编码代理自身注册的命令树。这些命令只使用 Brigadier 基础类型，
     * 因此不会产生带注册表名的参数类型。
     *
     * @param registry 命令参数类型注册表
     * @param type     Brigadier 参数类型
     * @return 对应的参数类型数据
     */
    public static ArgumentTypeData fromBrigadierType(CommandArgumentTypeRegistry registry,
                                                     com.mojang.brigadier.arguments.ArgumentType<?> type) {
        ArgumentTypeData data = new ArgumentTypeData(registry);

        switch (type) {
            case BoolArgumentType _ -> data.type = ArgumentType.BRIGADIER_BOOL;
            case FloatArgumentType floatType -> {
                data.type = ArgumentType.BRIGADIER_FLOAT;
                data.minFloat = floatType.getMinimum();
                data.maxFloat = floatType.getMaximum();
                data.numberFlags = 0;
                if (data.minFloat != -Float.MAX_VALUE) data.numberFlags |= 0x01;
                if (data.maxFloat != Float.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case DoubleArgumentType doubleType -> {
                data.type = ArgumentType.BRIGADIER_DOUBLE;
                data.minDouble = doubleType.getMinimum();
                data.maxDouble = doubleType.getMaximum();
                data.numberFlags = 0;
                if (data.minDouble != -Double.MAX_VALUE) data.numberFlags |= 0x01;
                if (data.maxDouble != Double.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case IntegerArgumentType intType -> {
                data.type = ArgumentType.BRIGADIER_INTEGER;
                data.minInt = intType.getMinimum();
                data.maxInt = intType.getMaximum();
                data.numberFlags = 0;
                if (data.minInt != Integer.MIN_VALUE) data.numberFlags |= 0x01;
                if (data.maxInt != Integer.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case LongArgumentType longType -> {
                data.type = ArgumentType.BRIGADIER_LONG;
                data.minLong = longType.getMinimum();
                data.maxLong = longType.getMaximum();
                data.numberFlags = 0;
                if (data.minLong != Long.MIN_VALUE) data.numberFlags |= 0x01;
                if (data.maxLong != Long.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case StringArgumentType stringType -> {
                data.type = ArgumentType.BRIGADIER_STRING;
                data.stringBehavior = switch (stringType.getType()) {
                    case SINGLE_WORD -> 0;
                    case QUOTABLE_PHRASE -> 1;
                    case GREEDY_PHRASE -> 2;
                };
            }
            case null, default -> {
                // 未知的 Brigadier 类型按可引用短语字符串处理，与 Minecraft 客户端的行为一致
                data.type = ArgumentType.BRIGADIER_STRING;
                data.stringBehavior = 1;
            }
        }

        return data;
    }

    /**
     * 按协议版本从缓冲区读取参数类型及其属性。
     *
     * @param buf             数据缓冲区
     * @param protocolVersion 当前协议版本，用于换算线上参数类型 ID
     * @throws IllegalArgumentException 参数类型 ID 在该协议版本中不存在时
     */
    public void read(ByteBuf buf, ProtocolVersion protocolVersion) {
        int protocolId = MinecraftCodecUtils.readVarInt(buf);
        type = registry.read(protocolVersion.getProtocolVersionCode(), protocolId);

        switch (type) {
            // BRIGADIER_BOOL 与其余无属性类型一样，网络上只有一个参数类型 ID
            case BRIGADIER_FLOAT -> {
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minFloat = buf.readFloat();
                if ((numberFlags & 0x02) != 0) maxFloat = buf.readFloat();
            }
            case BRIGADIER_DOUBLE -> {
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minDouble = buf.readDouble();
                if ((numberFlags & 0x02) != 0) maxDouble = buf.readDouble();
            }
            case BRIGADIER_INTEGER -> {
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minInt = buf.readInt();
                if ((numberFlags & 0x02) != 0) maxInt = buf.readInt();
            }
            case BRIGADIER_LONG -> {
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minLong = buf.readLong();
                if ((numberFlags & 0x02) != 0) maxLong = buf.readLong();
            }
            case BRIGADIER_STRING -> stringBehavior = MinecraftCodecUtils.readVarInt(buf);
            case ENTITY -> entityFlags = buf.readByte();
            case SCORE_HOLDER -> scoreHolderFlags = buf.readByte();
            case TIME -> minTime = buf.readInt();
            case RESOURCE_OR_TAG, RESOURCE_OR_TAG_KEY, RESOURCE, RESOURCE_KEY, RESOURCE_SELECTOR ->
                    registryKey = MinecraftCodecUtils.readString(buf);
            default -> {
                // 其余类型在网络上只有一个参数类型 ID，没有附加属性
            }
        }
    }

    /**
     * 按协议版本将参数类型及其属性写入缓冲区。
     *
     * @param buf             数据缓冲区
     * @param protocolVersion 目标协议版本，用于换算线上参数类型 ID
     * @throws IllegalArgumentException 参数类型在该协议版本中不存在时
     */
    public void write(ByteBuf buf, ProtocolVersion protocolVersion) {
        if (type == null) {
            throw new IllegalStateException("Cannot write an argument type before it has been set");
        }
        MinecraftCodecUtils.writeVarInt(buf, registry.write(protocolVersion.getProtocolVersionCode(), type));

        switch (type) {
            case BRIGADIER_FLOAT -> {
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeFloat(minFloat);
                if ((numberFlags & 0x02) != 0) buf.writeFloat(maxFloat);
            }
            case BRIGADIER_DOUBLE -> {
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeDouble(minDouble);
                if ((numberFlags & 0x02) != 0) buf.writeDouble(maxDouble);
            }
            case BRIGADIER_INTEGER -> {
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeInt(minInt);
                if ((numberFlags & 0x02) != 0) buf.writeInt(maxInt);
            }
            case BRIGADIER_LONG -> {
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeLong(minLong);
                if ((numberFlags & 0x02) != 0) buf.writeLong(maxLong);
            }
            case BRIGADIER_STRING -> MinecraftCodecUtils.writeVarInt(buf, stringBehavior);
            case ENTITY -> buf.writeByte(entityFlags);
            case SCORE_HOLDER -> buf.writeByte(scoreHolderFlags);
            case TIME -> buf.writeInt(minTime);
            case RESOURCE_OR_TAG, RESOURCE_OR_TAG_KEY, RESOURCE, RESOURCE_KEY, RESOURCE_SELECTOR ->
                    MinecraftCodecUtils.writeString(buf, registryKey != null ? registryKey : "");
            default -> {
                // 其余类型在网络上只有一个参数类型 ID，没有附加属性
            }
        }
    }

    /**
     * @return 参数类型，未设置时为 {@code null}
     */
    public ArgumentType getType() {
        return type;
    }

    /**
     * @param type 参数类型
     */
    public void setType(ArgumentType type) {
        this.type = type;
    }

    public byte getNumberFlags() {
        return numberFlags;
    }

    public void setNumberFlags(byte numberFlags) {
        this.numberFlags = numberFlags;
    }

    public float getMinFloat() {
        return minFloat;
    }

    public void setMinFloat(float minFloat) {
        this.minFloat = minFloat;
    }

    public float getMaxFloat() {
        return maxFloat;
    }

    public void setMaxFloat(float maxFloat) {
        this.maxFloat = maxFloat;
    }

    public double getMinDouble() {
        return minDouble;
    }

    public void setMinDouble(double minDouble) {
        this.minDouble = minDouble;
    }

    public double getMaxDouble() {
        return maxDouble;
    }

    public void setMaxDouble(double maxDouble) {
        this.maxDouble = maxDouble;
    }

    public int getMinInt() {
        return minInt;
    }

    public void setMinInt(int minInt) {
        this.minInt = minInt;
    }

    public int getMaxInt() {
        return maxInt;
    }

    public void setMaxInt(int maxInt) {
        this.maxInt = maxInt;
    }

    public long getMinLong() {
        return minLong;
    }

    public void setMinLong(long minLong) {
        this.minLong = minLong;
    }

    public long getMaxLong() {
        return maxLong;
    }

    public void setMaxLong(long maxLong) {
        this.maxLong = maxLong;
    }

    public int getStringBehavior() {
        return stringBehavior;
    }

    public void setStringBehavior(int stringBehavior) {
        this.stringBehavior = stringBehavior;
    }

    public byte getEntityFlags() {
        return entityFlags;
    }

    public void setEntityFlags(byte entityFlags) {
        this.entityFlags = entityFlags;
    }

    public byte getScoreHolderFlags() {
        return scoreHolderFlags;
    }

    public void setScoreHolderFlags(byte scoreHolderFlags) {
        this.scoreHolderFlags = scoreHolderFlags;
    }

    public int getMinTime() {
        return minTime;
    }

    public void setMinTime(int minTime) {
        this.minTime = minTime;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }
}
