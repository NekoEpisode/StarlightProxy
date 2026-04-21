package io.slidermc.starlight;

import io.slidermc.starlight.api.server.ProxiedServer;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.commands.ServerCommand;
import io.slidermc.starlight.config.StarlightConfig;
import io.slidermc.starlight.manager.ServerManager;
import io.slidermc.starlight.network.packet.PacketRegistry;
import io.slidermc.starlight.network.packet.RegistryPacketUtils;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundDisconnectConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundFinishConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.configuration.ClientboundPluginMessageConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.clientbound.login.*;
import io.slidermc.starlight.network.packet.packets.clientbound.play.*;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundPongResponsePacket;
import io.slidermc.starlight.network.packet.packets.clientbound.status.ClientboundStatusResponsePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.configuration.ServerboundClientInformationConfigurationPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.configuration.ServerboundFinishConfigurationAckPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.handshake.ServerboundHandshakePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundEncryptionResponsePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginAckPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundLoginStartPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.login.ServerboundPluginResponsePacket;
import io.slidermc.starlight.network.packet.packets.serverbound.play.ServerboundChatCommandPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.play.ServerboundClientInformationPlayPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.play.ServerboundCommandSuggestionPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.play.ServerboundConfigurationAckPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundPingRequestPacket;
import io.slidermc.starlight.network.packet.packets.serverbound.status.ServerboundStatusRequestPacket;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;
import io.slidermc.starlight.network.protocolenum.ProtocolVersion;
import io.slidermc.starlight.utils.AddressResolver;
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
            log.error("Cannot load configuration file, shutting down...", e);
            System.exit(1);
            return;
        }

        // 应用配置中的语言设置
        translateManager.setActiveLocale(config.getLanguage());

        log.info(translateManager.translate("starlight.logging.info.starting"));

        printASCIIArt();

        String defaultServer = config.getDefaultServer();
        if (defaultServer == null) {
            log.error(translateManager.translate("starlight.logging.error.config_dont_have_default_server"));
            System.exit(1);
            return;
        }
        if (!config.getServers().containsKey(defaultServer)) {
            log.error(translateManager.translate("starlight.logging.error.cannot_found_default_server"), defaultServer);
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
            log.error(translateManager.translate("starlight.logging.error.default_server_address_resolve_error"), defaultServer, e.getMessage());
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
                log.warn(translateManager.translate("starlight.logging.warn.server_address_resolve_failed"), entry.getKey(), e.getMessage());
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

        // 注册内置代理命令
        proxy.getCommandManager().register(new ServerCommand(proxy));
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
        r.registerListener(ClientboundStatusResponsePacket.class, "default", new ClientboundStatusResponsePacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.CLIENTBOUND, 0x01, ClientboundPongResponsePacket::new);
        r.registerListener(ClientboundPongResponsePacket.class, "default", new ClientboundPongResponsePacket.Listener());

        r.registerPacket(av, ProtocolState.LOGIN, ProtocolDirection.CLIENTBOUND, 0x00, ClientboundDisconnectLoginPacket::new);
        r.registerListener(ClientboundDisconnectLoginPacket.class, "default", new ClientboundDisconnectLoginPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:hello"), ProtocolState.LOGIN, ProtocolDirection.CLIENTBOUND, ClientboundEncryptionRequestPacket::new);
        r.registerListener(ClientboundEncryptionRequestPacket.class, "default", new ClientboundEncryptionRequestPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_compression"), ProtocolState.LOGIN, ProtocolDirection.CLIENTBOUND, ClientboundSetCompressionPacket::new);
        r.registerListener(ClientboundSetCompressionPacket.class, "default", new ClientboundSetCompressionPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:custom_query"), ProtocolState.LOGIN, ProtocolDirection.CLIENTBOUND, ClientboundPluginRequestPacket::new);
        r.registerListener(ClientboundPluginRequestPacket.class, "default", new ClientboundPluginRequestPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_finished"), ProtocolState.LOGIN, ProtocolDirection.CLIENTBOUND, ClientboundLoginSuccessPacket::new);
        r.registerListener(ClientboundLoginSuccessPacket.class, "default", new ClientboundLoginSuccessPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:disconnect"), ProtocolState.CONFIGURATION, ProtocolDirection.CLIENTBOUND, ClientboundDisconnectConfigurationPacket::new);
        r.registerListener(ClientboundDisconnectConfigurationPacket.class, "default", new ClientboundDisconnectConfigurationPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:finish_configuration"), ProtocolState.CONFIGURATION, ProtocolDirection.CLIENTBOUND, ClientboundFinishConfigurationPacket::new);
        r.registerListener(ClientboundFinishConfigurationPacket.class, "default", new ClientboundFinishConfigurationPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:custom_payload"), ProtocolState.CONFIGURATION, ProtocolDirection.CLIENTBOUND, ClientboundPluginMessageConfigurationPacket::new);
        r.registerListener(ClientboundPluginMessageConfigurationPacket.class, "default", new ClientboundPluginMessageConfigurationPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:disconnect"), ProtocolState.PLAY, ProtocolDirection.CLIENTBOUND, ClientboundDisconnectPlayPacket::new);
        r.registerListener(ClientboundDisconnectPlayPacket.class, "default", new ClientboundDisconnectPlayPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:start_configuration"), ProtocolState.PLAY, ProtocolDirection.CLIENTBOUND, ClientboundStartConfigurationPacket::new);
        r.registerListener(ClientboundStartConfigurationPacket.class, "default", new ClientboundStartConfigurationPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:system_chat"), ProtocolState.PLAY, ProtocolDirection.CLIENTBOUND, ClientboundSystemChatPacket::new);
        r.registerListener(ClientboundSystemChatPacket.class, "default", new ClientboundSystemChatPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:commands"), ProtocolState.PLAY, ProtocolDirection.CLIENTBOUND, ClientboundCommandsPacket::new);
        r.registerListener(ClientboundCommandsPacket.class, "default", new ClientboundCommandsPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:command_suggestions"), ProtocolState.PLAY, ProtocolDirection.CLIENTBOUND, ClientboundCommandSuggestionsPacket::new);
        r.registerListener(ClientboundCommandSuggestionsPacket.class, "default", new ClientboundCommandSuggestionsPacket.Listener());
    }

    private static void registerServerboundPackets(RegistryPacketUtils registryPacketUtils) {
        PacketRegistry r = registryPacketUtils.getPacketRegistry();
        int av = ProtocolVersion.ALL_VERSION.getProtocolVersionCode();

        r.registerPacket(av, ProtocolState.HANDSHAKE, ProtocolDirection.SERVERBOUND, 0x00, ServerboundHandshakePacket::new);
        r.registerListener(ServerboundHandshakePacket.class, "default", new ServerboundHandshakePacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.SERVERBOUND, 0x00, ServerboundStatusRequestPacket::new);
        r.registerListener(ServerboundStatusRequestPacket.class, "default", new ServerboundStatusRequestPacket.Listener());

        r.registerPacket(av, ProtocolState.STATUS, ProtocolDirection.SERVERBOUND, 0x01, ServerboundPingRequestPacket::new);
        r.registerListener(ServerboundPingRequestPacket.class, "default", new ServerboundPingRequestPacket.Listener());

        r.registerPacket(av, ProtocolState.LOGIN, ProtocolDirection.SERVERBOUND, 0x00, ServerboundLoginStartPacket::new);
        r.registerListener(ServerboundLoginStartPacket.class, "default", new ServerboundLoginStartPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:key"), ProtocolState.LOGIN, ProtocolDirection.SERVERBOUND, ServerboundEncryptionResponsePacket::new);
        r.registerListener(ServerboundEncryptionResponsePacket.class, "default", new ServerboundEncryptionResponsePacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:custom_query_answer"), ProtocolState.LOGIN, ProtocolDirection.SERVERBOUND, ServerboundPluginResponsePacket::new);
        r.registerListener(ServerboundPluginResponsePacket.class, "default", new ServerboundPluginResponsePacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:login_acknowledged"), ProtocolState.LOGIN, ProtocolDirection.SERVERBOUND, ServerboundLoginAckPacket::new);
        r.registerListener(ServerboundLoginAckPacket.class, "default", new ServerboundLoginAckPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:finish_configuration"), ProtocolState.CONFIGURATION, ProtocolDirection.SERVERBOUND, ServerboundFinishConfigurationAckPacket::new);
        r.registerListener(ServerboundFinishConfigurationAckPacket.class, "default", new ServerboundFinishConfigurationAckPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:client_information"), ProtocolState.CONFIGURATION, ProtocolDirection.SERVERBOUND, ServerboundClientInformationConfigurationPacket::new);
        r.registerListener(ServerboundClientInformationConfigurationPacket.class, "default", new ServerboundClientInformationConfigurationPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:configuration_acknowledged"), ProtocolState.PLAY, ProtocolDirection.SERVERBOUND, ServerboundConfigurationAckPacket::new);
        r.registerListener(ServerboundConfigurationAckPacket.class, "default", new ServerboundConfigurationAckPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:chat_command"), ProtocolState.PLAY, ProtocolDirection.SERVERBOUND, ServerboundChatCommandPacket::new);
        r.registerListener(ServerboundChatCommandPacket.class, "default", new ServerboundChatCommandPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:command_suggestion"), ProtocolState.PLAY, ProtocolDirection.SERVERBOUND, ServerboundCommandSuggestionPacket::new);
        r.registerListener(ServerboundCommandSuggestionPacket.class, "default", new ServerboundCommandSuggestionPacket.Listener());

        registryPacketUtils.registerByAutoMapping(Key.key("minecraft:client_information"), ProtocolState.PLAY, ProtocolDirection.SERVERBOUND, ServerboundClientInformationPlayPacket::new);
        r.registerListener(ServerboundClientInformationPlayPacket.class, "default", new ServerboundClientInformationPlayPacket.Listener());
    }
}
