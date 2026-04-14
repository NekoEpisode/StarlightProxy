package io.slidermc.starlight.network.packet;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
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
    private final Map<Integer, Map<ProtocolState, Map<ProtocolDirection, Map<Class<? extends IMinecraftPacket>, Integer>>>> reverseMap = new ConcurrentHashMap<>(); // protocolVersion(state(direction(packetClass(packetId))))
    private final Map<String, IPacketListener<?>> listenerMap = new ConcurrentHashMap<>(); // packetClassName → listener

    public void registerPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId, Supplier<? extends IMinecraftPacket> factory) {
        packetFactoryMap
                .computeIfAbsent(protocolVersion, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(state, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(direction, k -> new ConcurrentHashMap<>())
                .put(packetId, factory);
        reverseMap
                .computeIfAbsent(protocolVersion, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(state, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(direction, k -> new ConcurrentHashMap<>())
                .put(factory.get().getClass(), packetId);
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

        // 同步清理反向表
        Map<ProtocolDirection, Map<Class<? extends IMinecraftPacket>, Integer>> reverseStateMap =
                reverseMap.getOrDefault(protocolVersion, Map.of())
                          .getOrDefault(state, Map.of());
        Map<Class<? extends IMinecraftPacket>, Integer> reverseDirectionMap = reverseStateMap.get(direction);
        if (reverseDirectionMap != null) {
            reverseDirectionMap.values().remove(packetId);
        }
    }

    /**
     * 根据包实例反查 packetId，用于 Encoder。
     */
    public int getPacketId(int protocolVersion, ProtocolState state, ProtocolDirection direction, IMinecraftPacket packet) {
        Map<Class<? extends IMinecraftPacket>, Integer> classMap =
                reverseMap.getOrDefault(protocolVersion, Map.of())
                          .getOrDefault(state, Map.of())
                          .getOrDefault(direction, Map.of());
        Integer id = classMap.get(packet.getClass());
        if (id == null) {
            throw new IllegalArgumentException(
                    "No packetId registered for class: " + packet.getClass().getName()
                    + ", protocolVersion=" + protocolVersion
                    + ", state=" + state
                    + ", direction=" + direction
            );
        }
        return id;
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

    // -------------------------------------------------------------------------
    // Listener 注册 / 注销 / 分发
    // -------------------------------------------------------------------------

    public <T extends IMinecraftPacket> void registerListener(Class<T> packetClass, IPacketListener<T> listener) {
        listenerMap.put(packetClass.getName(), listener);
    }

    public <T extends IMinecraftPacket> void unregisterListener(Class<T> packetClass) {
        listenerMap.remove(packetClass.getName());
    }

    @SuppressWarnings("unchecked")
    public void dispatch(IMinecraftPacket packet, ChannelHandlerContext ctx) {
        IPacketListener<?> listener = listenerMap.get(packet.getClass().getName());
        if (listener != null) {
            ((IPacketListener<IMinecraftPacket>) listener).handle(packet, ctx);
        }
    }
}