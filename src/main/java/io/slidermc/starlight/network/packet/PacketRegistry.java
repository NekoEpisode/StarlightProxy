package io.slidermc.starlight.network.packet;

import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A registry for managing the creation and registration of Minecraft packets.
 * This class allows for the dynamic registration and unregistration of packet
 * factories, which are responsible for creating instances of {@link IMinecraftPacket}.
 * The registry supports multiple protocol versions, allowing different packet
 * implementations to be used based on the protocol version.
 */
public class PacketRegistry {
    private final Map<Integer, Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>>> packetFactoryMap = new ConcurrentHashMap<>(); // protocolVersion(state(direction(packetId(packetSupplier))))

    public void registerPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId, Supplier<? extends IMinecraftPacket> factory) {
        packetFactoryMap
                .computeIfAbsent(protocolVersion, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(state, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(direction, k -> new ConcurrentHashMap<>())
                .put(packetId, factory);
    }

    public void unregisterPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>> versionMap = packetFactoryMap.get(protocolVersion);
        if (versionMap == null) return;

        Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>> stateMap = versionMap.get(state);
        if (stateMap == null) return;

        Map<Integer, Supplier<? extends IMinecraftPacket>> directionMap = stateMap.get(direction);
        if (directionMap == null) return;

        directionMap.remove(packetId);
        if (directionMap.isEmpty()) {
            stateMap.remove(direction);
            if (stateMap.isEmpty()) {
                versionMap.remove(state);
                if (versionMap.isEmpty()) {
                    packetFactoryMap.remove(protocolVersion);
                }
            }
        }
    }

    public IMinecraftPacket createPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>> versionMap = packetFactoryMap.get(protocolVersion);
        if (versionMap == null) {
            throw new IllegalArgumentException("Unknown protocol version: " + protocolVersion);
        }

        Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>> stateMap = versionMap.get(state);
        if (stateMap == null) {
            throw new IllegalArgumentException("Unknown state: " + state + " for protocol version " + protocolVersion);
        }

        Map<Integer, Supplier<? extends IMinecraftPacket>> directionMap = stateMap.get(direction);
        if (directionMap == null) {
            throw new IllegalArgumentException("Unknown direction: " + direction + " for state " + state + " and protocol version " + protocolVersion);
        }

        Supplier<? extends IMinecraftPacket> factory = directionMap.get(packetId);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown packet id: " + packetId + " for direction " + direction + ", state " + state + ", protocol version " + protocolVersion);
        }

        return factory.get();
    }

    public boolean hasPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>> versionMap = packetFactoryMap.get(protocolVersion);
        if (versionMap == null) return false;

        Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>> stateMap = versionMap.get(state);
        if (stateMap == null) return false;

        Map<Integer, Supplier<? extends IMinecraftPacket>> directionMap = stateMap.get(direction);
        return directionMap != null && directionMap.containsKey(packetId);
    }
}