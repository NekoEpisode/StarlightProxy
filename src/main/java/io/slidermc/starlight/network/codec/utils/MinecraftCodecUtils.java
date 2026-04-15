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
        int length = readVarInt(byteBuf);
        byte[] bytes = new byte[length];
        byteBuf.readBytes(bytes);
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

        writeVarInt(byteBuf, gameProfile.properties().size());
        for (GameProfile.Property prop : gameProfile.properties()) {
            writeString(byteBuf, prop.name());
            writeString(byteBuf, prop.value());
            if (prop.signature() != null) {
                byteBuf.writeBoolean(true);
                writeString(byteBuf, prop.signature());
            } else {
                byteBuf.writeBoolean(false);
            }
        }
    }

    public static GameProfile readGameProfile(ByteBuf byteBuf) {
        UUID uuid = readUUID(byteBuf);
        String username = readString(byteBuf);

        int length = readVarInt(byteBuf);
        List<GameProfile.Property> properties = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            String name = readString(byteBuf);
            String value = readString(byteBuf);
            String signature = null;
            boolean hasSign = byteBuf.readBoolean();
            if (hasSign) {
                signature = readString(byteBuf);
            }
            GameProfile.Property property = new GameProfile.Property(name, value, signature);
            properties.add(property);
        }

        return new GameProfile(username, uuid, properties);
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

    public static Key readKey(ByteBuf byteBuf) {
        String key = readString(byteBuf);
        return Key.key(key);
    }
}
