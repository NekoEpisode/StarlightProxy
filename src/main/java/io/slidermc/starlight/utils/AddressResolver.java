package io.slidermc.starlight.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetSocketAddress;
import java.util.Hashtable;

/**
 * 将配置中的服务器地址字符串解析为 {@link InetSocketAddress}。
 *
 * <p>支持以下格式：
 * <ul>
 *   <li>{@code hostname} — 先尝试 Minecraft SRV 记录
 *       ({@code _minecraft._tcp.hostname})，若无 SRV 则使用默认端口 25565</li>
 *   <li>{@code hostname:port} — 直接使用给定主机名和端口（不查询 SRV）</li>
 *   <li>{@code 1.2.3.4:port} — 直接使用给定 IP 和端口</li>
 * </ul>
 */
public class AddressResolver {
    private static final Logger log = LoggerFactory.getLogger(AddressResolver.class);
    private static final int DEFAULT_MINECRAFT_PORT = 25565;

    /**
     * 解析服务器地址字符串。
     *
     * @param address 配置中的地址字符串
     * @return 解析后的 {@link InetSocketAddress}（主机名部分可能尚未解析为 IP，
     *         Netty 在 connect 时会自动完成 A/AAAA 解析）
     * @throws IllegalArgumentException 端口号格式不合法时抛出
     */
    public static InetSocketAddress resolve(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("地址不能为空");
        }

        // 找最后一个冒号，区分 "host:port" 和纯 "hostname"
        // 注意：IPv6 地址形如 [::1]:25565，这里用 lastIndexOf 正确处理
        int colonIdx = address.lastIndexOf(':');

        String host;
        int port;
        boolean portExplicit;

        if (colonIdx < 0) {
            // 没有冒号：纯主机名，无显式端口
            host = address.trim();
            port = DEFAULT_MINECRAFT_PORT;
            portExplicit = false;
        } else {
            String portStr = address.substring(colonIdx + 1).trim();
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("端口超出范围(1-65535): " + port);
                }
                host = address.substring(0, colonIdx).trim();
                portExplicit = true;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("端口号不合法: \"" + portStr + "\"");
            }
        }

        // 只有未显式指定端口时才查询 SRV
        if (!portExplicit) {
            InetSocketAddress srvResult = trySrvLookup(host);
            if (srvResult != null) {
                log.info("SRV解析: {} → {}:{}", host, srvResult.getHostString(), srvResult.getPort());
                return srvResult;
            }
            log.debug("未找到 SRV 记录，使用默认端口: {}:{}", host, DEFAULT_MINECRAFT_PORT);
        }

        return new InetSocketAddress(host, port);
    }

    /**
     * 查询 {@code _minecraft._tcp.<hostname>} SRV 记录。
     *
     * @return 解析出的地址；若无 SRV 记录或查询失败则返回 {@code null}
     */
    private static InetSocketAddress trySrvLookup(String hostname) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns:");
            // 超时设置：初始超时1s，最多重试1次，避免卡住启动
            env.put("com.sun.jndi.dns.timeout.initial", "1000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext ctx = new InitialDirContext(env);

            String srvName = "_minecraft._tcp." + hostname;
            log.debug("查询 SRV 记录: {}", srvName);
            Attributes attrs = ctx.getAttributes(srvName, new String[]{"SRV"});
            ctx.close();

            Attribute srvAttr = attrs.get("SRV");
            if (srvAttr == null || srvAttr.size() == 0) {
                return null;
            }

            // SRV 记录格式: <priority> <weight> <port> <target>
            // 实现中只取第一条记录（优先级最高），完整实现需按 priority/weight 排序
            String record = srvAttr.get(0).toString();
            String[] parts = record.split("\\s+");
            if (parts.length < 4) {
                log.warn("SRV 记录格式不正确，已忽略: {}", record);
                return null;
            }

            int srvPort = Integer.parseInt(parts[2]);
            String srvHost = parts[3];
            // DNS 名称末尾可能有一个点
            if (srvHost.endsWith(".")) {
                srvHost = srvHost.substring(0, srvHost.length() - 1);
            }

            return new InetSocketAddress(srvHost, srvPort);

        } catch (NamingException e) {
            // NXDOMAIN / 无 SRV 记录是正常情况，仅 debug 级别
            log.debug("SRV 查询失败 ({}): {}", hostname, e.getMessage());
            return null;
        } catch (Exception e) {
            // DNS 上下文初始化失败（例如 jdk.naming.dns 模块不可用）
            log.warn("SRV 查询异常，已跳过: {}", e.getMessage());
            return null;
        }
    }
}

