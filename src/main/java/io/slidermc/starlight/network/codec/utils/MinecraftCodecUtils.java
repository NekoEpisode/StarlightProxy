package io.slidermc.starlight.network.codec.utils;

import io.netty.buffer.ByteBuf;
import io.slidermc.starlight.api.profile.GameProfile;
import net.kyori.adventure.key.Key;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinecraftCodecUtils {
    /**
     * Reads a variable-length encoded integer from the provided ByteBuf.
     *
     * @param byteBuf The ByteBuf from which to read the VarInt.
     * @return The decoded integer value.
     * @throws RuntimeException If the VarInt is too large, indicating possible data corruption or an encoding error.
     */
    public static int readVarInt(ByteBuf byteBuf) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = byteBuf.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new RuntimeException("VarInt is too big");
            }
        } while ((read & 0b10000000) != 0);

        return result;
    }

    /**
     * Writes a variable-length encoded integer to the provided ByteBuf.
     *
     * @param byteBuf The ByteBuf to which the VarInt will be written.
     * @param value The integer value to encode and write.
     */
    public static void writeVarInt(ByteBuf byteBuf, int value) {
        while ((value & 0xFFFFFF80) != 0) {
            byteBuf.writeByte((value & 0b01111111) | 0b10000000);
            value >>>= 7;
        }
        byteBuf.writeByte(value);
    }

    public static void writeString(ByteBuf byteBuf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(byteBuf, bytes.length);
        byteBuf.writeBytes(bytes);
    }

    public static String readString(ByteBuf byteBuf) {
        byte[] bytes = readByteArray(byteBuf);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUUID(ByteBuf byteBuf, java.util.UUID uuid) {
        byteBuf.writeLong(uuid.getMostSignificantBits());
        byteBuf.writeLong(uuid.getLeastSignificantBits());
    }

    public static java.util.UUID readUUID(ByteBuf byteBuf) {
        return new java.util.UUID(byteBuf.readLong(), byteBuf.readLong());
    }

    public static void writeGameProfile(ByteBuf byteBuf, GameProfile gameProfile) {
        writeUUID(byteBuf, gameProfile.uuid());
        writeString(byteBuf, gameProfile.username());
        writeProperties(byteBuf, gameProfile.properties());
    }

    public static GameProfile readGameProfile(ByteBuf byteBuf) {
        UUID uuid = readUUID(byteBuf);
        String username = readString(byteBuf);
        return new GameProfile(username, uuid, readProperties(byteBuf));
    }

    public static void writeByteArray(ByteBuf byteBuf, byte[] array) {
        writeVarInt(byteBuf, array.length);
        byteBuf.writeBytes(array);
    }

    public static byte[] readByteArray(ByteBuf byteBuf) {
        int length = readVarInt(byteBuf);
        byte[] bytes = new byte[length];
        byteBuf.readBytes(bytes);
        return bytes;
    }

    public static void writeKey(ByteBuf byteBuf, Key key) {
        writeString(byteBuf, key.namespace() + ":" + key.value());
    }

    @SuppressWarnings("PatternValidation")
    public static Key readKey(ByteBuf byteBuf) {
        String key = readString(byteBuf);
        return Key.key(key);
    }

    /**
     * 计算给定值编码为 VarInt 所需的字节数（1–5）。
     */
    public static int varIntSize(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    public static void writeProperties(ByteBuf buf, List<GameProfile.Property> properties) {
        writeVarInt(buf, properties.size());
        for (GameProfile.Property property : properties) {
            writeString(buf, property.name());
            writeString(buf, property.value());
            String signature = property.signature();
            if (signature != null && !signature.isEmpty()) {
                buf.writeBoolean(true);
                writeString(buf, signature);
            } else {
                buf.writeBoolean(false);
            }
        }
    }

    public static List<GameProfile.Property> readProperties(ByteBuf buf) {
        int size = readVarInt(buf);
        List<GameProfile.Property> properties = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = readString(buf);
            String value = readString(buf);
            String signature = null;
            boolean hasSignature = buf.readBoolean();
            if (hasSignature) {
                signature = readString(buf);
            }
            properties.add(new GameProfile.Property(name, value, signature));
        }
        return properties;
    }

    /**
     * 读取 Position (8 bytes)
     * x: 26-bit signed, y: 12-bit signed, z: 26-bit signed
     */
    public static int[] readPosition(ByteBuf buf) {
        long val = buf.readLong();
        int x = (int) (val >> 38);
        int y = (int) ((val << 52) >> 52);
        int z = (int) ((val << 26 >> 38));
        return new int[]{x, y, z};
    }

    /**
     * 写 Position
     */
    public static void writePosition(ByteBuf buf, int x, int y, int z) {
        long val = (((long)x & 0x3FFFFFF) << 38) | (((long)z & 0x3FFFFFF) << 12) | ((long)y & 0xFFF);
        buf.writeLong(val);
    }
}
