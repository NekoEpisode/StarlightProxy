package io.slidermc.starlight.api.channel;

import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code minecraft:register} / {@code minecraft:unregister} 的载荷编解码。
 *
 * <p>载荷格式为若干通道名以 {@code \0} 分隔的 UTF-8 字符串，无结尾分隔符，
 * 通道名遵循 Identifier 格式（未写命名空间时默认为 {@code minecraft}）。
 */
public final class ChannelPayload {

    /** 通道声明包自身的通道名。 */
    public static final Key REGISTER_CHANNEL = Key.key("minecraft:register");

    /** 通道注销包自身的通道名。 */
    public static final Key UNREGISTER_CHANNEL = Key.key("minecraft:unregister");

    /** 客户端声明的通道名允许的最大长度，与 Identifier 的 32767 上限保持一致。 */
    private static final int MAX_CHANNEL_NAME_LENGTH = 32767;

    private static final Logger log = LoggerFactory.getLogger(ChannelPayload.class);

    private ChannelPayload() {
    }

    /**
     * 解析通道名列表。
     *
     * <p>空载荷返回空集合：直接对空串按 {@code \0} 切分会得到含一个空字符串的数组，
     * 进而产生一个非法的通道名。
     *
     * <p>无法解析为合法通道名的条目会被跳过而非抛异常——载荷来自客户端，不应让畸形输入
     * 中断整条注册流程。跳过的条目仅记录 debug 日志（本类属于 API 层，拿不到
     * TranslateManager，而项目要求非 debug 日志必须走翻译）。
     *
     * @param data 载荷字节
     * @return 解析出的通道集合，保持出现顺序
     */
    public static Set<Key> parse(byte[] data) {
        if (data == null || data.length == 0) {
            return Set.of();
        }

        String payload = new String(data, StandardCharsets.UTF_8);
        String[] names = payload.split("\0", -1);

        Set<Key> channels = new LinkedHashSet<>(names.length);
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_CHANNEL_NAME_LENGTH) {
                log.debug("Skipped an over-long plugin channel name ({} characters)", name.length());
                continue;
            }

            try {
                channels.add(Key.key(name));
            } catch (Exception ex) {
                log.debug("Skipped an invalid plugin channel name: {}", name);
            }
        }

        return channels;
    }

    /**
     * 编码通道名列表。
     *
     * @param channels 待编码的通道，不得为空
     * @return 以 {@code \0} 分隔的 UTF-8 载荷
     * @throws IllegalArgumentException 若 {@code channels} 为空
     */
    public static byte[] write(Set<Key> channels) {
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("At least one channel is required");
        }

        return String.join("\0", channels.stream().map(Key::asString).toList())
                .getBytes(StandardCharsets.UTF_8);
    }
}
