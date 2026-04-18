package io.slidermc.starlight.api.command.source;

import net.kyori.adventure.key.Key;

import java.util.Objects;

/**
 * 类型安全的上下文键，用于插件间通过 {@link IStarlightCommandSource} 交换数据。
 *
 * <p>推荐在插件的静态常量中声明 key，避免字符串硬编码：
 * <pre>{@code
 * public static final ContextKey<String> REAL_IP =
 *         ContextKey.of(Key.key("plugin:xxx"), String.class);
 * }</pre>
 *
 * @param <T> 值的类型
 */
public final class ContextKey<T> {
    private final Key key;
    private final Class<T> type;

    private ContextKey(Key key, Class<T> type) {
        this.key = key;
        this.type = type;
    }

    public static <T> ContextKey<T> of(Key key, Class<T> type) {
        if (type == null) throw new IllegalArgumentException("ContextKey type must not be null");
        return new ContextKey<>(key, type);
    }

    public Key key() {
        return key;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ContextKey<?> that = (ContextKey<?>) o;
        return Objects.equals(key, that.key) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type);
    }

    @Override
    public String toString() {
        return "ContextKey{" +
                "key=" + key +
                ", type=" + type +
                '}';
    }
}

