package io.slidermc.starlight.network.codec.utils;

import io.netty.buffer.ByteBuf;

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
        while ((value & 0b10000000) != 0) {
            byteBuf.writeByte((value & 0b01111111) | 0b10000000);
            value >>>= 7;
        }
        byteBuf.writeByte(value);
    }
}
