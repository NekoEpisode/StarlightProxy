package io.slidermc.starlight.network.command;

import com.mojang.brigadier.arguments.*;
import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.network.codec.utils.MinecraftCodecUtils;

/**
 * 参数类型数据
 * 表示命令参数的类型和属性
 */
public class ArgumentTypeData {
    // Brigadier 基础类型
    public static final int BRIGADIER_BOOL = 0;
    public static final int BRIGADIER_FLOAT = 1;
    public static final int BRIGADIER_DOUBLE = 2;
    public static final int BRIGADIER_INTEGER = 3;
    public static final int BRIGADIER_LONG = 4;
    public static final int BRIGADIER_STRING = 5;
    
    // Minecraft 特定类型
    public static final int MINECRAFT_ENTITY = 6;
    public static final int MINECRAFT_GAME_PROFILE = 7;
    public static final int MINECRAFT_BLOCK_POS = 8;
    public static final int MINECRAFT_COLUMN_POS = 9;
    public static final int MINECRAFT_VEC3 = 10;
    public static final int MINECRAFT_VEC2 = 11;
    public static final int MINECRAFT_BLOCK_STATE = 12;
    public static final int MINECRAFT_BLOCK_PREDICATE = 13;
    public static final int MINECRAFT_ITEM_STACK = 14;
    public static final int MINECRAFT_ITEM_PREDICATE = 15;
    public static final int MINECRAFT_COLOR = 16;
    public static final int MINECRAFT_HEX_COLOR = 17;
    public static final int MINECRAFT_COMPONENT = 18;
    public static final int MINECRAFT_STYLE = 19;
    public static final int MINECRAFT_MESSAGE = 20;
    public static final int MINECRAFT_NBT_COMPOUND_TAG = 21;
    public static final int MINECRAFT_NBT_TAG = 22;
    public static final int MINECRAFT_NBT_PATH = 23;
    public static final int MINECRAFT_OBJECTIVE = 24;
    public static final int MINECRAFT_OBJECTIVE_CRITERIA = 25;
    public static final int MINECRAFT_OPERATION = 26;
    public static final int MINECRAFT_PARTICLE = 27;
    public static final int MINECRAFT_ANGLE = 28;
    public static final int MINECRAFT_ROTATION = 29;
    public static final int MINECRAFT_SCOREBOARD_SLOT = 30;
    public static final int MINECRAFT_SCORE_HOLDER = 31;
    public static final int MINECRAFT_SWIZZLE = 32;
    public static final int MINECRAFT_TEAM = 33;
    public static final int MINECRAFT_ITEM_SLOT = 34;
    public static final int MINECRAFT_ITEM_SLOTS = 35;
    public static final int MINECRAFT_RESOURCE_LOCATION = 36;
    public static final int MINECRAFT_FUNCTION = 37;
    public static final int MINECRAFT_ENTITY_ANCHOR = 38;
    public static final int MINECRAFT_INT_RANGE = 39;
    public static final int MINECRAFT_FLOAT_RANGE = 40;
    public static final int MINECRAFT_DIMENSION = 41;
    public static final int MINECRAFT_GAMEMODE = 42;
    public static final int MINECRAFT_TIME = 43;
    public static final int MINECRAFT_RESOURCE_OR_TAG = 44;
    public static final int MINECRAFT_RESOURCE_OR_TAG_KEY = 45;
    public static final int MINECRAFT_RESOURCE = 46;
    public static final int MINECRAFT_RESOURCE_KEY = 47;
    public static final int MINECRAFT_RESOURCE_SELECTOR = 48;
    public static final int MINECRAFT_TEMPLATE_MIRROR = 49;
    public static final int MINECRAFT_TEMPLATE_ROTATION = 50;
    public static final int MINECRAFT_HEIGHTMAP = 51;
    public static final int MINECRAFT_LOOT_TABLE = 52;
    public static final int MINECRAFT_LOOT_PREDICATE = 53;
    public static final int MINECRAFT_LOOT_MODIFIER = 54;
    public static final int MINECRAFT_DIALOG = 55;
    public static final int MINECRAFT_UUID = 56;
    
    private int parserId;
    
    // 存储原始属性数据用于写回
    private byte numberFlags;
    private float minFloat, maxFloat;
    private double minDouble, maxDouble;
    private int minInt, maxInt;
    private long minLong, maxLong;
    private int stringBehavior;
    private byte entityFlags;
    private byte scoreHolderFlags;
    private int minTime;
    private String registry;
    
    public ArgumentTypeData() {
    }
    
    public ArgumentTypeData(int parserId) {
        this.parserId = parserId;
    }
    
    /**
     * 从Brigadier参数类型创建ArgumentTypeData
     */
    public static ArgumentTypeData fromBrigadierType(ArgumentType<?> type) {
        ArgumentTypeData data = new ArgumentTypeData();

        switch (type) {
            case BoolArgumentType _ -> data.parserId = BRIGADIER_BOOL;
            case FloatArgumentType floatType -> {
                data.parserId = BRIGADIER_FLOAT;
                data.minFloat = floatType.getMinimum();
                data.maxFloat = floatType.getMaximum();
                data.numberFlags = 0;
                if (data.minFloat != -Float.MAX_VALUE) data.numberFlags |= 0x01;
                if (data.maxFloat != Float.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case DoubleArgumentType doubleType -> {
                data.parserId = BRIGADIER_DOUBLE;
                data.minDouble = doubleType.getMinimum();
                data.maxDouble = doubleType.getMaximum();
                data.numberFlags = 0;
                if (data.minDouble != -Double.MAX_VALUE) data.numberFlags |= 0x01;
                if (data.maxDouble != Double.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case IntegerArgumentType intType -> {
                data.parserId = BRIGADIER_INTEGER;
                data.minInt = intType.getMinimum();
                data.maxInt = intType.getMaximum();
                data.numberFlags = 0;
                if (data.minInt != Integer.MIN_VALUE) data.numberFlags |= 0x01;
                if (data.maxInt != Integer.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case LongArgumentType longType -> {
                data.parserId = BRIGADIER_LONG;
                data.minLong = longType.getMinimum();
                data.maxLong = longType.getMaximum();
                data.numberFlags = 0;
                if (data.minLong != Long.MIN_VALUE) data.numberFlags |= 0x01;
                if (data.maxLong != Long.MAX_VALUE) data.numberFlags |= 0x02;
            }
            case StringArgumentType stringType -> {
                data.parserId = BRIGADIER_STRING;
                switch (stringType.getType()) {
                    case SINGLE_WORD:
                        data.stringBehavior = 0;
                        break;
                    case QUOTABLE_PHRASE:
                        data.stringBehavior = 1;
                        break;
                    case GREEDY_PHRASE:
                        data.stringBehavior = 2;
                        break;
                }
            }
            case null, default -> {
                // 默认字符串类型
                data.parserId = BRIGADIER_STRING;
                data.stringBehavior = 1;
            }
        }
        
        return data;
    }
    
    public void read(ByteBuf buf) {
        parserId = MinecraftCodecUtils.readVarInt(buf);
        
        switch (parserId) {
            case BRIGADIER_BOOL:
                // 无属性
                break;
                
            case BRIGADIER_FLOAT:
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minFloat = buf.readFloat();
                if ((numberFlags & 0x02) != 0) maxFloat = buf.readFloat();
                break;
                
            case BRIGADIER_DOUBLE:
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minDouble = buf.readDouble();
                if ((numberFlags & 0x02) != 0) maxDouble = buf.readDouble();
                break;
                
            case BRIGADIER_INTEGER:
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minInt = buf.readInt();
                if ((numberFlags & 0x02) != 0) maxInt = buf.readInt();
                break;
                
            case BRIGADIER_LONG:
                numberFlags = buf.readByte();
                if ((numberFlags & 0x01) != 0) minLong = buf.readLong();
                if ((numberFlags & 0x02) != 0) maxLong = buf.readLong();
                break;
                
            case BRIGADIER_STRING:
                stringBehavior = MinecraftCodecUtils.readVarInt(buf);
                break;
                
            case MINECRAFT_ENTITY:
                entityFlags = buf.readByte();
                break;
                
            case MINECRAFT_SCORE_HOLDER:
                scoreHolderFlags = buf.readByte();
                break;
                
            case MINECRAFT_TIME:
                minTime = buf.readInt();
                break;
                
            case MINECRAFT_RESOURCE_OR_TAG:
            case MINECRAFT_RESOURCE_OR_TAG_KEY:
            case MINECRAFT_RESOURCE:
            case MINECRAFT_RESOURCE_KEY:
            case MINECRAFT_RESOURCE_SELECTOR:
                registry = MinecraftCodecUtils.readString(buf);
                break;
                
            // 以下类型无属性
            case MINECRAFT_GAME_PROFILE:
            case MINECRAFT_BLOCK_POS:
            case MINECRAFT_COLUMN_POS:
            case MINECRAFT_VEC3:
            case MINECRAFT_VEC2:
            case MINECRAFT_BLOCK_STATE:
            case MINECRAFT_BLOCK_PREDICATE:
            case MINECRAFT_ITEM_STACK:
            case MINECRAFT_ITEM_PREDICATE:
            case MINECRAFT_COLOR:
            case MINECRAFT_HEX_COLOR:
            case MINECRAFT_COMPONENT:
            case MINECRAFT_STYLE:
            case MINECRAFT_MESSAGE:
            case MINECRAFT_NBT_COMPOUND_TAG:
            case MINECRAFT_NBT_TAG:
            case MINECRAFT_NBT_PATH:
            case MINECRAFT_OBJECTIVE:
            case MINECRAFT_OBJECTIVE_CRITERIA:
            case MINECRAFT_OPERATION:
            case MINECRAFT_PARTICLE:
            case MINECRAFT_ANGLE:
            case MINECRAFT_ROTATION:
            case MINECRAFT_SCOREBOARD_SLOT:
            case MINECRAFT_SWIZZLE:
            case MINECRAFT_TEAM:
            case MINECRAFT_ITEM_SLOT:
            case MINECRAFT_ITEM_SLOTS:
            case MINECRAFT_RESOURCE_LOCATION:
            case MINECRAFT_FUNCTION:
            case MINECRAFT_ENTITY_ANCHOR:
            case MINECRAFT_INT_RANGE:
            case MINECRAFT_FLOAT_RANGE:
            case MINECRAFT_DIMENSION:
            case MINECRAFT_GAMEMODE:
            case MINECRAFT_TEMPLATE_MIRROR:
            case MINECRAFT_TEMPLATE_ROTATION:
            case MINECRAFT_HEIGHTMAP:
            case MINECRAFT_LOOT_TABLE:
            case MINECRAFT_LOOT_PREDICATE:
            case MINECRAFT_LOOT_MODIFIER:
            case MINECRAFT_DIALOG:
            case MINECRAFT_UUID:
                // 无属性
                break;
                
            default:
                throw new IllegalArgumentException("Unknown parser ID: " + parserId);
        }
    }
    
    public void write(ByteBuf buf) {
        MinecraftCodecUtils.writeVarInt(buf, parserId);
        
        switch (parserId) {
            case BRIGADIER_BOOL:
                break;
                
            case BRIGADIER_FLOAT:
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeFloat(minFloat);
                if ((numberFlags & 0x02) != 0) buf.writeFloat(maxFloat);
                break;
                
            case BRIGADIER_DOUBLE:
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeDouble(minDouble);
                if ((numberFlags & 0x02) != 0) buf.writeDouble(maxDouble);
                break;
                
            case BRIGADIER_INTEGER:
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeInt(minInt);
                if ((numberFlags & 0x02) != 0) buf.writeInt(maxInt);
                break;
                
            case BRIGADIER_LONG:
                buf.writeByte(numberFlags);
                if ((numberFlags & 0x01) != 0) buf.writeLong(minLong);
                if ((numberFlags & 0x02) != 0) buf.writeLong(maxLong);
                break;
                
            case BRIGADIER_STRING:
                MinecraftCodecUtils.writeVarInt(buf, stringBehavior);
                break;
                
            case MINECRAFT_ENTITY:
                buf.writeByte(entityFlags);
                break;
                
            case MINECRAFT_SCORE_HOLDER:
                buf.writeByte(scoreHolderFlags);
                break;
                
            case MINECRAFT_TIME:
                buf.writeInt(minTime);
                break;
                
            case MINECRAFT_RESOURCE_OR_TAG:
            case MINECRAFT_RESOURCE_OR_TAG_KEY:
            case MINECRAFT_RESOURCE:
            case MINECRAFT_RESOURCE_KEY:
            case MINECRAFT_RESOURCE_SELECTOR:
                MinecraftCodecUtils.writeString(buf, registry != null ? registry : "");
                break;
                
            default:
                // 无属性
                break;
        }
    }

    public int getParserId() {
        return parserId;
    }

    public void setParserId(int parserId) {
        this.parserId = parserId;
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

    public double getMinDouble() {
        return minDouble;
    }

    public void setMinDouble(double minDouble) {
        this.minDouble = minDouble;
    }

    public float getMaxFloat() {
        return maxFloat;
    }

    public void setMaxFloat(float maxFloat) {
        this.maxFloat = maxFloat;
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

    public byte getEntityFlags() {
        return entityFlags;
    }

    public void setEntityFlags(byte entityFlags) {
        this.entityFlags = entityFlags;
    }

    public int getStringBehavior() {
        return stringBehavior;
    }

    public void setStringBehavior(int stringBehavior) {
        this.stringBehavior = stringBehavior;
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

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }
}
