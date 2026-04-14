package io.slidermc.starlight;

import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.packet.packets.serverbound.handshake.ServerboundHandshakePacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main() {
        log.info("Loading I18N...");
        TranslateManager translateManager = new TranslateManager();
        translateManager.loadBuiltin();

        log.info(translateManager.translate("starlight.logging.info.starting"));

        printASCIIArt();

        RegistryPacketUtils registryPacketUtils = new RegistryPacketUtils(new PacketRegistry(), translateManager);

        log.info(translateManager.translate("starlight.logging.info.packet.registering"));

        registerPackets(registryPacketUtils);

        StarlightProxy proxy = new StarlightProxy(
                new InetSocketAddress("0.0.0.0", 8000),
                translateManager,
                registryPacketUtils
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

    }

    private static void registerServerboundPackets(RegistryPacketUtils registryPacketUtils) {
        registryPacketUtils.getPacketRegistry().registerPacket(ProtocolVersion.UNKNOWN_OR_PLACEHOLDER.getProtocolVersionCode(), ProtocolState.HANDSHAKE, ProtocolDirection.SERVERBOUND, 0x00, ServerboundHandshakePacket::new); // 特殊处理
    }
}
