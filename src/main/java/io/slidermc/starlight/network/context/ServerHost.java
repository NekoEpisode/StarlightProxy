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

        if (serverAddress.isEmpty()) {
            throw new IllegalArgumentException("Server address is empty");
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