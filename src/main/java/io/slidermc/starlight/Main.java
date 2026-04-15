package io.slidermc.starlight;

import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.manager.ServerManager;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.utils.AddressResolver;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundDisconnectLoginPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.ClientboundLoginSuccessPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundPongResponsePacket;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.handshake.ServerboundHandshakePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginAckPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginStartPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundPingRequestPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundStatusRequestPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main() {
        log.info("Loading I18N...");
        TranslateManager translateManager = new TranslateManager();
        translateManager.loadBuiltin();

        // 加载配置（不存在则从内置资源复制）
        StarlightConfig config;
        try {
            config = StarlightConfig.loadOrCreate(Path.of("config.yml"));
        } catch (IOException e) {
            log.error("无法加载配置文件，代理终止", e);
            System.exit(1);
            return;
        }

        // 应用配置中的语言设置
        translateManager.setActiveLocale(config.getLanguage());

        log.info(translateManager.translate("starlight.logging.info.starting"));

        printASCIIArt();

        String defaultServer = config.getDefaultServer();
        if (defaultServer == null) {
            log.error("配置文件中缺少default-server设置项，请先设置！");
            System.exit(1);
            return;
        }
        if (!config.getServers().containsKey(defaultServer)) {
            log.error("未找到此default-server: {}", defaultServer);
            System.exit(1);
            return;
        }
        // ---- 加载默认服务器 ------------------------------------------------
        StarlightConfig.ServerEntry defaultEntry = config.getServers().get(defaultServer);
        InetSocketAddress defaultAddr;
        try {
            log.debug("加载默认服务器: {} ({})", defaultServer, defaultEntry.address());
            defaultAddr = AddressResolver.resolve(defaultEntry.address());
        } catch (IllegalArgumentException e) {
            log.error("默认服务器 {} 地址解析失败: {}", defaultServer, e.getMessage());
            System.exit(1);
            return;
        }
        ServerManager serverManager = new ServerManager(new ProxiedServer(defaultAddr, defaultServer));

        // ---- 加载其余服务器 ------------------------------------------------
        for (Map.Entry<String, StarlightConfig.ServerEntry> entry : config.getServers().entrySet()) {
            if (entry.getKey().equals(defaultServer)) continue;
            log.debug("加载服务器: {} ({})", entry.getKey(), entry.getValue().address());
            try {
                InetSocketAddress addr = AddressResolver.resolve(entry.getValue().address());
                serverManager.addServer(new ProxiedServer(addr, entry.getKey()));
            } catch (IllegalArgumentException e) {
                log.warn("服务器 {} 地址解析失败，跳过: {}", entry.getKey(), e.getMessage());
            }
        }

        RegistryPacketUtils registryPacketUtils = new RegistryPacketUtils(new PacketRegistry(), translateManager);
        registryPacketUtils.loadMappings();

        log.info(translateManager.translate("starlight.logging.info.packet.registering"));

        registerPackets(registryPacketUtils);

        StarlightProxy proxy = new StarlightProxy(
                new InetSocketAddress(config.getHost(), config.getPort()),
                translateManager,
                registryPacketUtils,
                config,
                serverManager
        );
        proxy.start();
    }

    private static void printASCIIArt() {
        log.info("\n" + """
                   _____  __                __ _         __     __      \s
                  / ___/ / /_ ____ _ _____ / /(_)____ _ / /_   / /_ __/|_
                  \\__ \\ / __// __ `// ___// // // __ `// __ \\ / __/|    /
                 ___/ // /_ / /_/ // /   / // // /_/ // / / // /_ /_ __|\s
                /____/ \\__/ \\__,_//_/   /_//_/ \\__, //_/ /_/ \\__/  |/   \s
                                              /____/                    \s
                """);
    }

    private static void registerPackets(RegistryPacketUtils registryPacketUtils) {
        registerClientboundPackets(registryPacketUtils);
        registerServerboundPackets(registryPacketUtils);
    }

    private static void registerClientboundPackets(RegistryPacketUtils registryPacketUtils) {
        PacketRegistry r = registryPacketUtils.getPacketRegistry();
        int av = ProtocolVersion.ALL_VERSION.getProtocolVersionCode();

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.CLIENTBOUND, 0x00, ClientboundStatusResponsePacket::new);
        r.registerListener(ClientboundStatusResponsePacket.class, new ClientboundStatusResponsePacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.CLIENTBOUND, 0x01, ClientboundPongResponsePacket::new);
        r.registerListener(ClientboundPongResponsePacket.class, new ClientboundPongResponsePacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_disconnect"), ClientboundDisconnectLoginPacket::new);
        r.registerListener(ClientboundDisconnectLoginPacket.class, new ClientboundDisconnectLoginPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_finished"), ClientboundLoginSuccessPacket::new);
        r.registerListener(ClientboundLoginSuccessPacket.class, new ClientboundLoginSuccessPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:disconnect"), ClientboundDisconnectConfigurationPacket::new);
        r.registerListener(ClientboundDisconnectConfigurationPacket.class, new ClientboundDisconnectConfigurationPacket.Listener());
    }

    private static void registerServerboundPackets(RegistryPacketUtils registryPacketUtils) {
        PacketRegistry r = registryPacketUtils.getPacketRegistry();
        int av = ProtocolVersion.ALL_VERSION.getProtocolVersionCode();

        r.registerPacket(av, ProtocolState.HANDSHAKE, ProtocolDirection.SERVERBOUND, 0x00, ServerboundHandshakePacket::new);
        r.registerListener(ServerboundHandshakePacket.class, new ServerboundHandshakePacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.SERVERBOUND, 0x00, ServerboundStatusRequestPacket::new);
        r.registerListener(ServerboundStatusRequestPacket.class, new ServerboundStatusRequestPacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.SERVERBOUND, 0x01, ServerboundPingRequestPacket::new);
        r.registerListener(ServerboundPingRequestPacket.class, new ServerboundPingRequestPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:hello"), ServerboundLoginStartPacket::new);
        r.registerListener(ServerboundLoginStartPacket.class, new ServerboundLoginStartPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_acknowledged"), ServerboundLoginAckPacket::new);
        r.registerListener(ServerboundLoginAckPacket.class, new ServerboundLoginAckPacket.Listener());
    }
}
