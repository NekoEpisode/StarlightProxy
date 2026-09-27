package io.slidermc.starlight.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class StarlightConfig {
    private static final Logger log = LoggerFactory.getLogger(StarlightConfig.class);

    private final String host;
    private final int port;
    private final int maxPlayers;
    private final boolean onlineMode;
    private final boolean encryption;
    private final String ipForwardType;
    private final String forwardSecret;
    private final String motd;
    private final String brand;
    private final String language;
    private final boolean loggingCommand;
    private final int compressThreshold;
    private final String iconFilePath;
    private final boolean passThroughHostname;

    private final Map<String, ServerEntry> servers;

    private final Map<String, String> forcedHost;

    public record ServerEntry(String address) {}

    public StarlightConfig(String host, int port, int maxPlayers, boolean onlineMode,
                           boolean encryption, String ipForwardType, String forwardSecret, String motd, String brand,
                           String language, boolean loggingCommand, int compressThreshold, String iconFilePath,
                           boolean passThroughHostname,
                           Map<String, ServerEntry> servers, Map<String, String> forcedHost) {
        this.host = host;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.onlineMode = onlineMode;
        this.encryption = encryption;
        this.ipForwardType = ipForwardType;
        this.forwardSecret = forwardSecret;
        this.motd = motd;
        this.brand = brand;
        this.language = language;
        this.loggingCommand = loggingCommand;
        this.compressThreshold = compressThreshold;
        this.iconFilePath = iconFilePath;
        this.passThroughHostname = passThroughHostname;
        this.servers = Collections.unmodifiableMap(servers);
        this.forcedHost = Collections.unmodifiableMap(forcedHost);
    }

    // -------------------------------------------------------------------------
    // 加载
    // -------------------------------------------------------------------------

    /**
     * 若 configPath 文件不存在，从 classpath 复制默认 config.yml；
     * 然后用 SnakeYAML 解析并返回 StarlightConfig 实例。
     */
    public static StarlightConfig loadOrCreate(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            log.info("Configuration file not found; copying default configuration from built-in resources to: {}", configPath.toAbsolutePath());
            try (InputStream src = StarlightConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (src == null) {
                    throw new IOException("Built-in resource config.yml not found; unable to create default configuration file.");
                }
                if (configPath.getParent() != null) {
                    Files.createDirectories(configPath.getParent());
                }
                Files.copy(src, configPath);
            }
        }

        log.info("Loaded configuration: {}", configPath.toAbsolutePath());
        try (InputStream in = Files.newInputStream(configPath)) {
            Map<String, Object> root = new Yaml().load(in);
            return parse(root);
        }
    }

    @SuppressWarnings("unchecked")
    private static StarlightConfig parse(Map<String, Object> root) {
        Map<String, Object> proxy = (Map<String, Object>) root.get("proxy");

        String host                  = (String)  proxy.get("host");
        int    port                  = (int)      proxy.get("port");
        int    maxPlayers            = (int)      proxy.get("max-players");
        boolean onlineMode           = (boolean)  proxy.get("online-mode");
        boolean encryption           = onlineMode || (boolean) proxy.get("encryption");
        String ipForwardType         = (String)   proxy.get("forward-type");
        String forwardSecret         = (String)   proxy.get("forward-secret");
        String motd                  = (String)   proxy.get("motd");
        String brand                 = (String)   proxy.get("brand");
        String language              = (String)   proxy.get("language");
        boolean loggingCommand       = (boolean)  proxy.get("logging-command");
        int compressThreshold        = (int)      proxy.get("compress-threshold");
        String iconFilePath          = (String)   proxy.get("icon-file-path");
        Object passThroughRaw        = proxy.get("pass-through-hostname");
        boolean passThroughHostname  = passThroughRaw == null || (boolean) passThroughRaw;

        Map<String, ServerEntry> servers = new LinkedHashMap<>();
        Object serversRaw = root.get("servers");
        if (serversRaw instanceof Map<?, ?> serversMap) {
            for (Map.Entry<?, ?> e : serversMap.entrySet()) {
                String name = e.getKey().toString();
                Map<String, Object> data = (Map<String, Object>) e.getValue();
                servers.put(name, new ServerEntry((String) data.get("address")));
            }
        }

        Map<String, String> forcedHost = new LinkedHashMap<>();
        Object forcedHostsRaw = root.get("forced-host");
        if (forcedHostsRaw instanceof Map<?, ?> forcedHostsMap) {
            for (Map.Entry<?, ?> e : forcedHostsMap.entrySet()) {
                String address = e.getKey().toString();
                String serverName = e.getValue().toString();
                forcedHost.put(address, serverName);
            }
        }
        log.debug("Force Hosts: {}", forcedHost);

        return new StarlightConfig(host, port, maxPlayers, onlineMode, encryption, ipForwardType, forwardSecret,
                motd, brand, language, loggingCommand, compressThreshold, iconFilePath, passThroughHostname,
                servers, forcedHost);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getHost()               { return host; }
    public int getPort()                  { return port; }
    public int getMaxPlayers()            { return maxPlayers; }
    public boolean isOnlineMode()         { return onlineMode; }
    public boolean isEncryption()         { return encryption; }
    public String getForwardType()        { return ipForwardType; }
    public String getForwardSecret()      { return forwardSecret; }
    public String getMotd()               { return motd; }
    public String getBrand()              { return brand; }
    public String getLanguage()           { return language; }
    public boolean isLoggingCommand()     { return loggingCommand; }
    public int getCompressThreshold()     { return compressThreshold; }
    public String getIconFilePath()       { return iconFilePath; }

    /**
     * 是否把客户端握手里的地址透传给下游服务器。
     *
     * <p>开启时下游握手使用客户端请求的地址（含 {@code \0} 之后附加的数据），
     * 使后端的虚拟主机配置与依赖该字段的插件（如 Floodgate）能够正常工作；
     * 关闭时沿用后端配置中的地址，并忽略客户端地址。
     *
     * @return 启用透传返回 {@code true}，默认为 {@code true}
     */
    public boolean isPassThroughHostname() { return passThroughHostname; }
    public Map<String, ServerEntry> getServers() { return servers; }
    public Map<String, String> getForcedHost() { return forcedHost; }
}
