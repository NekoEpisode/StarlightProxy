package io.slidermc.starlight.utils;

public record UnsignedByte(short value) {
    private static final int MIN = 0;
    private static final int MAX = 255;

    public UnsignedByte {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("Value must be between 0 and 255");
        }
    }

    public static UnsignedByte of(int value) {
        return new UnsignedByte((short) value);
    }

    public static UnsignedByte ofByte(byte b) {
        return new UnsignedByte((short) (b & 0xFF));
    }

    public UnsignedByte add(int value) {
        return of(this.value + value);
    }

    public UnsignedByte and(UnsignedByte other) {
        return of(this.value & other.value);
    }

    public UnsignedByte or(UnsignedByte other) {
        return of(this.value | other.value);
    }

    public UnsignedByte xor(UnsignedByte other) {
        return of(this.value ^ other.value);
    }

    public UnsignedByte shiftLeft(int bits) {
        return of((this.value << bits) & 0xFF);
    }

    public UnsignedByte shiftRight(int bits) {
        return of((this.value >> bits) & 0xFF);
    }

    public boolean[] toBits() {
        boolean[] bits = new boolean[8];
        int val = this.value;
        for (int i = 7; i >= 0; i--) {
            bits[i] = (val & 1) == 1;
            val >>= 1;
        }
        return bits;
    }

    public static UnsignedByte fromBits(boolean[] bits) {
        if (bits.length != 8) {
            throw new IllegalArgumentException("Need exactly 8 bits");
        }
        short result = 0;
        for (int i = 0; i < 8; i++) {
            if (bits[i]) {
                result |= (short) (1 << (7 - i));
            }
        }
        return new UnsignedByte(result);
    }
}