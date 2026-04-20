package io.slidermc.starlight.config;

public class InternalConfig {
    public static final String VERSION_STRING = "Starlight 1.21.11-26.1";

    public static final String HANDLER_DECODER = "decoder";
    public static final String HANDLER_ENCODER = "encoder";
    public static final String HANDLER_COMPRESS = "compress";
    public static final String HANDLER_DECOMPRESS = "decompress";
    public static final String HANDLER_ENCRYPT = "encrypt";
    public static final String HANDLER_DECRYPT = "decrypt";
    public static final String HANDLER_MAIN = "handler";

    /** 解压缩超过此字节数时抛出异常，防止 zip-bomb 攻击（8 MiB）。 */
    public static final int MAX_UNCOMPRESSED_SIZE = 8 * 1024 * 1024;
}
