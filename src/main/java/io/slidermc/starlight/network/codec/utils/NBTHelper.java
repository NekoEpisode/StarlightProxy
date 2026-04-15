package io.slidermc.starlight.network.codec.utils;

import com.google.gson.*;
import com.google.gson.internal.LazilyParsedNumber;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.kyori.adventure.nbt.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minecraft 网络 NBT 工具类，适用于 1.20.2+ 协议。
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>从 {@link ByteBuf} 读写网络格式的 {@link BinaryTag}（无根标签名）</li>
 *   <li>{@link Component} 与 NBT/JSON 互转（用于聊天、标题等文本数据包）</li>
 *   <li>常用 {@link CompoundBinaryTag} 操作（合并、文本提取等）</li>
 * </ul>
 */
public class NBTHelper {

    private static final Logger log = LoggerFactory.getLogger(NBTHelper.class);
    private static final GsonComponentSerializer GSON_COMPONENT = GsonComponentSerializer.gson();
    private static final Gson GSON = new Gson();

    private NBTHelper() {}

    // -------------------------------------------------------------------------
    // Component ↔ NBT
    // -------------------------------------------------------------------------

    /**
     * 从 {@code buf} 读取网络 NBT 并反序列化为 {@link Component}。
     *
     * @param buf 数据源，读取后游标前移
     * @return 反序列化的 Component；读取失败时返回 {@link Component#empty()}
     */
    public static Component readComponent(ByteBuf buf) {
        CompoundBinaryTag nbt = readCompound(buf);
        if (nbt == null) {
            return Component.empty();
        }
        JsonElement json = nbtToJson(nbt);
        return GSON_COMPONENT.deserialize(GSON.toJson(json));
    }

    /**
     * 将 {@link Component} 序列化为网络 NBT 并写入 {@code buf}。
     *
     * @param buf       目标缓冲区
     * @param component 要写入的 Component
     */
    public static void writeComponent(ByteBuf buf, Component component) {
        String jsonStr = GSON_COMPONENT.serialize(component);
        JsonElement json = GSON.fromJson(jsonStr, JsonElement.class);
        BinaryTag nbt = jsonToNbt(json);
        writeNetworkNBT(buf, nbt);
    }

    // -------------------------------------------------------------------------
    // 网络 NBT 读写（底层）
    // -------------------------------------------------------------------------

    /**
     * 从 {@code buf} 读取网络格式 NBT（1.20.2+ 协议，无根标签名）。
     *
     * <p>支持 {@link StringBinaryTag} 和 {@link CompoundBinaryTag} 两种首字节类型。
     *
     * @param buf 数据源
     * @return 读取到的 {@link BinaryTag}；异常时返回空 {@link CompoundBinaryTag}
     */
    public static BinaryTag readNetworkNBT(ByteBuf buf) {
        if (buf.readableBytes() == 0) {
            return CompoundBinaryTag.empty();
        }
        buf.markReaderIndex();
        try {
            byte tagType = buf.readByte();
            buf.resetReaderIndex();
            if (tagType == BinaryTagTypes.STRING.id()) {
                return readStringTag(buf);
            } else if (tagType == BinaryTagTypes.COMPOUND.id()) {
                return readCompoundTag(buf);
            } else {
                log.warn("Unsupported NBT tag type: {}", tagType);
                return CompoundBinaryTag.empty();
            }
        } catch (Exception e) {
            log.error("Failed to determine NBT tag type", e);
            return CompoundBinaryTag.empty();
        }
    }

    /**
     * 将 {@link BinaryTag} 以网络格式（1.20.2+，无根标签名）写入 {@code buf}。
     *
     * <p>目前支持 {@link CompoundBinaryTag} 和 {@link StringBinaryTag}；
     * 其他类型将 fallback 为空 Compound。
     *
     * @param buf 目标缓冲区
     * @param nbt 要写入的 NBT 标签
     */
    public static void writeNetworkNBT(ByteBuf buf, BinaryTag nbt) {
        try {
            ByteBufOutputStream output = new ByteBufOutputStream(buf);
            if (nbt instanceof CompoundBinaryTag) {
                BinaryTagIO.writer().writeNameless((CompoundBinaryTag) nbt, (OutputStream) output);
            } else if (nbt instanceof StringBinaryTag stringTag) {
                buf.writeByte(BinaryTagTypes.STRING.id());
                byte[] bytes = stringTag.value().getBytes(StandardCharsets.UTF_8);
                buf.writeShort(bytes.length);
                buf.writeBytes(bytes);
            } else {
                log.warn("Unsupported NBT type for writing: {}", nbt.type());
                writeEmptyCompound(buf);
            }
        } catch (IOException e) {
            log.error("Failed to write network NBT", e);
            writeEmptyCompound(buf);
        }
    }

    // -------------------------------------------------------------------------
    // CompoundBinaryTag 便捷读写
    // -------------------------------------------------------------------------

    /**
     * 从 {@code buf} 读取网络 NBT 并转为 {@link CompoundBinaryTag}。
     *
     * <p>若读取到 {@link StringBinaryTag}，将其包装为 {@code {text: "..."}};
     * 其他类型返回空 Compound。
     *
     * @param buf 数据源
     * @return 读取到的 CompoundBinaryTag，不会返回 {@code null}
     */
    public static CompoundBinaryTag readCompound(ByteBuf buf) {
        BinaryTag tag = readNetworkNBT(buf);
        if (tag instanceof CompoundBinaryTag compound) {
            return compound;
        } else if (tag instanceof StringBinaryTag stringTag) {
            return CompoundBinaryTag.builder()
                    .putString("text", stringTag.value())
                    .build();
        }
        log.warn("Expected CompoundBinaryTag or StringBinaryTag, got {}", tag != null ? tag.type() : "null");
        return CompoundBinaryTag.empty();
    }

    /**
     * 将 {@link CompoundBinaryTag} 以网络格式写入 {@code buf}。
     *
     * @param buf      目标缓冲区
     * @param compound 要写入的 Compound
     */
    public static void writeCompound(ByteBuf buf, CompoundBinaryTag compound) {
        writeNetworkNBT(buf, compound);
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    /**
     * 构造仅含 {@code text} 字段的文本组件 NBT。
     *
     * @param text 文本内容
     * @return 对应的 {@link CompoundBinaryTag}
     */
    public static CompoundBinaryTag createTextComponent(String text) {
        return CompoundBinaryTag.builder()
                .putString("text", text)
                .build();
    }

    /**
     * 从文本组件 NBT 中提取 {@code text} 字段值。
     *
     * @param nbt 文本组件 NBT
     * @return text 字段的值，不存在时返回空字符串
     */
    public static String extractText(CompoundBinaryTag nbt) {
        return nbt.getString("text");
    }

    /**
     * 判断 NBT 是否包含 {@code text} 字段（即是否为文本组件）。
     *
     * @param nbt 待检测的 NBT
     * @return 包含 {@code text} 字段时返回 {@code true}
     */
    public static boolean isTextComponent(CompoundBinaryTag nbt) {
        return nbt.get("text") != null;
    }

    /**
     * 读取网络 NBT 并返回其调试字符串表示，用于日志排查。
     *
     * @param buf 数据源（读取后游标前移）
     * @return NBT 的字符串表示；数据无效时返回 {@code "Invalid NBT data"}
     */
    public static String nbtToString(ByteBuf buf) {
        try {
            return readNetworkNBT(buf).toString();
        } catch (Exception e) {
            return "Invalid NBT data";
        }
    }

    /**
     * 将 {@code second} 合并到 {@code first} 中（相同 key 以 {@code second} 为准）。
     *
     * @param first  基础 Compound
     * @param second 覆盖 Compound
     * @return 合并后的新 {@link CompoundBinaryTag}
     */
    public static CompoundBinaryTag mergeCompounds(CompoundBinaryTag first, CompoundBinaryTag second) {
        CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
        for (String key : first.keySet()) {
            builder.put(key, Objects.requireNonNull(first.get(key)));
        }
        for (String key : second.keySet()) {
            builder.put(key, Objects.requireNonNull(second.get(key)));
        }
        return builder.build();
    }

    // -------------------------------------------------------------------------
    // 私有辅助
    // -------------------------------------------------------------------------

    private static StringBinaryTag readStringTag(ByteBuf buf) {
        try {
            buf.readByte(); // tag type byte
            int length = buf.readUnsignedShort();
            byte[] bytes = new byte[length];
            buf.readBytes(bytes);
            return StringBinaryTag.stringBinaryTag(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to read string tag", e);
            return StringBinaryTag.stringBinaryTag("");
        }
    }

    private static CompoundBinaryTag readCompoundTag(ByteBuf buf) {
        try {
            return BinaryTagIO.reader().readNameless((InputStream) new ByteBufInputStream(buf));
        } catch (IOException e) {
            log.error("Failed to read compound tag", e);
            return CompoundBinaryTag.empty();
        }
    }

    private static void writeEmptyCompound(ByteBuf buf) {
        try {
            BinaryTagIO.writer().write(CompoundBinaryTag.empty(), (OutputStream) new ByteBufOutputStream(buf));
        } catch (IOException e) {
            buf.writeByte(BinaryTagTypes.COMPOUND.id());
            buf.writeByte(0);
            buf.writeByte(0);
        }
    }

    /**
     * 将 {@link BinaryTag} 递归转换为 {@link JsonElement}，用于 Component 反序列化。
     */
    private static JsonElement nbtToJson(BinaryTag tag) {
        if (tag == null) return JsonNull.INSTANCE;

        switch (tag.type().id()) {
            case 1:  return new JsonPrimitive(((ByteBinaryTag) tag).value());
            case 2:  return new JsonPrimitive(((ShortBinaryTag) tag).value());
            case 3:  return new JsonPrimitive(((IntBinaryTag) tag).value());
            case 4:  return new JsonPrimitive(((LongBinaryTag) tag).value());
            case 5:  return new JsonPrimitive(((FloatBinaryTag) tag).value());
            case 6:  return new JsonPrimitive(((DoubleBinaryTag) tag).value());
            case 8:  return new JsonPrimitive(((StringBinaryTag) tag).value());
            case 7: {
                byte[] arr = ((ByteArrayBinaryTag) tag).value();
                JsonArray a = new JsonArray(arr.length);
                for (byte b : arr) a.add(new JsonPrimitive(b));
                return a;
            }
            case 11: {
                int[] arr = ((IntArrayBinaryTag) tag).value();
                JsonArray a = new JsonArray(arr.length);
                for (int i : arr) a.add(new JsonPrimitive(i));
                return a;
            }
            case 12: {
                long[] arr = ((LongArrayBinaryTag) tag).value();
                JsonArray a = new JsonArray(arr.length);
                for (long l : arr) a.add(new JsonPrimitive(l));
                return a;
            }
            case 9: {
                ListBinaryTag list = (ListBinaryTag) tag;
                JsonArray a = new JsonArray(list.size());
                for (BinaryTag item : list) a.add(nbtToJson(item));
                return a;
            }
            case 10: {
                CompoundBinaryTag compound = (CompoundBinaryTag) tag;
                JsonObject obj = new JsonObject();
                for (String key : compound.keySet()) {
                    // 空 key 在列表文本组件中表示 "text"
                    obj.add(key.isEmpty() ? "text" : key, nbtToJson(compound.get(key)));
                }
                return obj;
            }
            default: return JsonNull.INSTANCE;
        }
    }

    /**
     * 将 {@link JsonElement} 递归转换为 {@link BinaryTag}，用于 Component 序列化。
     */
    private static BinaryTag jsonToNbt(JsonElement json) {
        if (json == null || json.isJsonNull()) return EndBinaryTag.endBinaryTag();

        if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isBoolean()) return ByteBinaryTag.byteBinaryTag((byte) (p.getAsBoolean() ? 1 : 0));
            if (p.isString())  return StringBinaryTag.stringBinaryTag(p.getAsString());
            if (p.isNumber()) {
                Number n = p.getAsNumber();
                if (n instanceof Byte)               return ByteBinaryTag.byteBinaryTag((Byte) n);
                if (n instanceof Short)              return ShortBinaryTag.shortBinaryTag((Short) n);
                if (n instanceof Integer)            return IntBinaryTag.intBinaryTag((Integer) n);
                if (n instanceof Long)               return LongBinaryTag.longBinaryTag((Long) n);
                if (n instanceof Float)              return FloatBinaryTag.floatBinaryTag((Float) n);
                if (n instanceof Double)             return DoubleBinaryTag.doubleBinaryTag((Double) n);
                if (n instanceof LazilyParsedNumber) return IntBinaryTag.intBinaryTag(n.intValue());
            }
        }

        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                builder.put(entry.getKey(), jsonToNbt(entry.getValue()));
            }
            return builder.build();
        }

        if (json.isJsonArray()) {
            JsonArray arr = json.getAsJsonArray();
            if (arr.isEmpty()) return ListBinaryTag.empty();

            List<BinaryTag> items = new ArrayList<>(arr.size());
            BinaryTagType<? extends BinaryTag> listType = null;
            for (JsonElement el : arr) {
                BinaryTag t = jsonToNbt(el);
                items.add(t);
                listType = (listType == null) ? t.type()
                        : (listType != t.type() ? BinaryTagTypes.COMPOUND : listType);
            }

            // 同质数组优化为原生数组标签
            if (listType == BinaryTagTypes.BYTE) {
                byte[] bytes = new byte[arr.size()];
                for (int i = 0; i < bytes.length; i++) bytes[i] = arr.get(i).getAsNumber().byteValue();
                return ByteArrayBinaryTag.byteArrayBinaryTag(bytes);
            }
            if (listType == BinaryTagTypes.INT) {
                int[] ints = new int[arr.size()];
                for (int i = 0; i < ints.length; i++) ints[i] = arr.get(i).getAsNumber().intValue();
                return IntArrayBinaryTag.intArrayBinaryTag(ints);
            }
            if (listType == BinaryTagTypes.LONG) {
                long[] longs = new long[arr.size()];
                for (int i = 0; i < longs.length; i++) longs[i] = arr.get(i).getAsNumber().longValue();
                return LongArrayBinaryTag.longArrayBinaryTag(longs);
            }

            // 混合类型列表：非 Compound 元素包装为 Compound
            if (listType == BinaryTagTypes.COMPOUND) {
                for (int i = 0; i < items.size(); i++) {
                    BinaryTag t = items.get(i);
                    if (t.type() != BinaryTagTypes.COMPOUND) {
                        items.set(i, CompoundBinaryTag.builder().put("", t).build());
                    }
                }
            }

            if (listType != null) return ListBinaryTag.listBinaryTag(listType, items);
        }

        return EndBinaryTag.endBinaryTag();
    }
}

