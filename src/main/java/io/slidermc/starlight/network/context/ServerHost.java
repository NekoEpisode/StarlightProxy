package io.slidermc.starlight.network.context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ServerHost {
    private static final int MAX_BYTES = 1024;

    private final String raw;
    private final String serverAddress;
    private final Map<String, Optional<String>> args;

    private ServerHost(String raw, String serverAddress, Map<String, Optional<String>> args) {
        this.raw = raw;
        this.serverAddress = serverAddress;
        this.args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    public String getRaw() {
        return raw;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public Map<String, Optional<String>> getArgs() {
        return args;
    }

    public Optional<String> getArg(String key) {
        return args.getOrDefault(key, Optional.empty());
    }

    public boolean hasArg(String key) {
        return args.containsKey(key);
    }

    /**
     * 解析握手包里的服务器地址（虚拟主机）。
     *
     * <p>地址可以为空：部分客户端（含 Geyser 的探测连接）会发送空地址，含义是"未指定虚拟主机"，
     * 此时所有查询都返回空、由调用方回退到默认服务器。空地址不是错误，因此不抛异常。
     *
     * @param raw 握手包中的原始地址，可包含 {@code ?} 查询串
     * @return 解析结果
     * @throws IllegalArgumentException 地址超过 {@value #MAX_BYTES} 字节时抛出
     */
    public static ServerHost parseFrom(String raw) {
        Objects.requireNonNull(raw, "raw");

        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Server address is too long");
        }

        int question = raw.indexOf('?');
        String serverAddress;
        String query;

        if (question < 0) {
            serverAddress = raw;
            query = "";
        } else {
            serverAddress = raw.substring(0, question);
            query = raw.substring(question + 1);
        }

        Map<String, Optional<String>> args = new LinkedHashMap<>();

        if (!query.isEmpty()) {
            // -1 保留末尾空段，例如 a=1&&b=2&
            for (String part : query.split("&", -1)) {
                if (part.isEmpty()) {
                    continue;
                }

                int eq = part.indexOf('=');
                String rawKey;
                String rawValue;

                if (eq < 0) {
                    rawKey = part;
                    rawValue = null;
                } else {
                    rawKey = part.substring(0, eq);
                    rawValue = part.substring(eq + 1);
                }

                String key = urlDecode(rawKey);

                // 语义：
                // "key"   -> Optional.empty()
                // "key="  -> Optional.of("")
                Optional<String> value = rawValue == null
                        ? Optional.empty()
                        : Optional.of(urlDecode(rawValue));

                args.put(key, value);
            }
        }

        return new ServerHost(raw, serverAddress, args);
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL encoding: " + s, e);
        }
    }

    @Override
    public String toString() {
        return "ServerHost{" +
                "raw='" + raw + '\'' +
                ", serverAddress='" + serverAddress + '\'' +
                ", args=" + args +
                '}';
    }
}