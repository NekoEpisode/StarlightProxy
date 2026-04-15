package io.slidermc.starlight;

import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundPongResponsePacket;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.handshake.ServerboundHandshakePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundPingRequestPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundStatusRequestPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

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

        RegistryPacketUtils registryPacketUtils = new RegistryPacketUtils(new PacketRegistry(), translateManager);
        registryPacketUtils.loadMappings();

        log.info(translateManager.translate("starlight.logging.info.packet.registering"));

        registerPackets(registryPacketUtils);

        StarlightProxy proxy = new StarlightProxy(
                new InetSocketAddress(config.getHost(), config.getPort()),
                translateManager,
                registryPacketUtils,
                config
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
    }
}
