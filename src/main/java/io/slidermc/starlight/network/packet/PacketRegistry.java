package io.slidermc.starlight.network.packet;

import io.netty.channel.ChannelHandlerContext;
import io.slidermc.starlight.StarlightProxy;
import io.slidermc.starlight.network.packet.listener.IPacketListener;
import io.slidermc.starlight.network.protocolenum.ProtocolDirection;
import io.slidermc.starlight.network.protocolenum.ProtocolState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * A registry for managing the creation and registration of Minecraft packets.
 * This class allows for the dynamic registration and unregistration of packet
 * factories, which are responsible for creating instances of {@link IMinecraftPacket}.
 * The registry supports multiple protocol versions, allowing different packet
 * implementations to be used based on the protocol version.
 *
 * <p>Packet lookup follows a two-step fallback strategy: the specific protocol version
 * is checked first; if no match is found, the lookup falls back to {@code ALL_VERSION}
 * ({@value ALL_VERSION}). See {@code docs/PROTOCOL_VERSION_FALLBACK.md} for details.
 */
public class PacketRegistry {
    /** Sentinel value matching {@code ProtocolVersion.ALL_VERSION}. Packets registered under
     *  this version act as a fallback for any protocol version that has no specific registration. */
    private static final int ALL_VERSION = -1;

    private final Map<Integer, Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>>> packetFactoryMap = new ConcurrentHashMap<>(); // protocolVersion(state(direction(packetId(packetSupplier))))
    private final Map<Integer, Map<ProtocolState, Map<ProtocolDirection, Map<Class<? extends IMinecraftPacket>, Integer>>>> reverseMap = new ConcurrentHashMap<>(); // protocolVersion(state(direction(packetClass(packetId))))
    private final Map<String, Map<String, IPacketListener<?>>> listenerMap = new ConcurrentHashMap<>(); // packetClassName → (listenerId → listener)

    /** Protects atomic cross-map updates between packetFactoryMap and reverseMap. */
    private final ReentrantReadWriteLock registryLock = new ReentrantReadWriteLock();

    public void registerPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId, Supplier<? extends IMinecraftPacket> factory) {
        Class<? extends IMinecraftPacket> packetClass = factory.get().getClass();
        registryLock.writeLock().lock();
        try {
            packetFactoryMap
                    .computeIfAbsent(protocolVersion, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(state, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(direction, k -> new ConcurrentHashMap<>())
                    .put(packetId, factory);
            reverseMap
                    .computeIfAbsent(protocolVersion, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(state, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(direction, k -> new ConcurrentHashMap<>())
                    .put(packetClass, packetId);
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    public void unregisterPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        registryLock.writeLock().lock();
        try {
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
                if (reverseDirectionMap.isEmpty()) {
                    reverseStateMap.remove(direction);
                    if (reverseStateMap.isEmpty()) {
                        Map<ProtocolState, Map<ProtocolDirection, Map<Class<? extends IMinecraftPacket>, Integer>>> revVersionMap = reverseMap.get(protocolVersion);
                        if (revVersionMap != null) {
                            revVersionMap.remove(state);
                            if (revVersionMap.isEmpty()) {
                                reverseMap.remove(protocolVersion);
                            }
                        }
                    }
                }
            }
        } finally {
            registryLock.writeLock().unlock();
        }
    }

    /**
     * 根据包实例反查 packetId，用于 Encoder。
     * 先按具体版本查，找不到再 fallback 到 ALL_VERSION（-1）。
     */
    public int getPacketId(int protocolVersion, ProtocolState state, ProtocolDirection direction, IMinecraftPacket packet) {
        registryLock.readLock().lock();
        try {
            Integer id = lookupPacketId(protocolVersion, state, direction, packet.getClass());
            if (id == null && protocolVersion != ALL_VERSION) {
                id = lookupPacketId(ALL_VERSION, state, direction, packet.getClass());
            }
            if (id == null) {
                throw new IllegalArgumentException(
                        "No packetId registered for class: " + packet.getClass().getName()
                        + ", protocolVersion=" + protocolVersion
                        + ", state=" + state
                        + ", direction=" + direction
                        + " (also not found under ALL_VERSION)"
                );
            }
            return id;
        } finally {
            registryLock.readLock().unlock();
        }
    }

    private Integer lookupPacketId(int protocolVersion, ProtocolState state, ProtocolDirection direction, Class<? extends IMinecraftPacket> clazz) {
        return reverseMap.getOrDefault(protocolVersion, Map.of())
                         .getOrDefault(state, Map.of())
                         .getOrDefault(direction, Map.of())
                         .get(clazz);
    }

    public IMinecraftPacket createPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        registryLock.readLock().lock();
        try {
            // 1. 先按具体版本查
            Supplier<? extends IMinecraftPacket> factory = lookupFactory(protocolVersion, state, direction, packetId);

            // 2. 找不到则 fallback 到 ALL_VERSION（-1），避免对每个版本重复注册版本无关的包
            if (factory == null && protocolVersion != ALL_VERSION) {
                factory = lookupFactory(ALL_VERSION, state, direction, packetId);
            }

            if (factory == null) {
                throw new IllegalArgumentException(
                        "Unknown packet id: " + packetId
                        + " for direction=" + direction
                        + ", state=" + state
                        + ", protocolVersion=" + protocolVersion
                        + " (also not found under ALL_VERSION)"
                );
            }

            return factory.get();
        } finally {
            registryLock.readLock().unlock();
        }
    }

    /** 内部纯查找，找不到任意一层时返回 null，不抛异常。 */
    private Supplier<? extends IMinecraftPacket> lookupFactory(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        Map<ProtocolState, Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>>> versionMap = packetFactoryMap.get(protocolVersion);
        if (versionMap == null) return null;
        Map<ProtocolDirection, Map<Integer, Supplier<? extends IMinecraftPacket>>> stateMap = versionMap.get(state);
        if (stateMap == null) return null;
        Map<Integer, Supplier<? extends IMinecraftPacket>> directionMap = stateMap.get(direction);
        if (directionMap == null) return null;
        return directionMap.get(packetId);
    }

    public boolean hasPacket(int protocolVersion, ProtocolState state, ProtocolDirection direction, int packetId) {
        registryLock.readLock().lock();
        try {
            if (lookupFactory(protocolVersion, state, direction, packetId) != null) return true;
            if (protocolVersion != ALL_VERSION) {
                return lookupFactory(ALL_VERSION, state, direction, packetId) != null;
            }
            return false;
        } finally {
            registryLock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Listener 注册 / 注销 / 分发
    // -------------------------------------------------------------------------

    /**
     * 注册一个 Listener，同一个包可注册多个 Listener，以 {@code id} 区分。
     * 若同一 packetClass + id 已存在，则覆盖。
     */
    public <T extends IMinecraftPacket> void registerListener(Class<T> packetClass, String id, IPacketListener<T> listener) {
        listenerMap
                .computeIfAbsent(packetClass.getName(), k -> new ConcurrentHashMap<>())
                .put(id, listener);
    }

    /**
     * 注销指定 id 的 Listener。
     */
    public <T extends IMinecraftPacket> void unregisterListener(Class<T> packetClass, String id) {
        Map<String, IPacketListener<?>> listeners = listenerMap.get(packetClass.getName());
        if (listeners != null) {
            listeners.remove(id);
            if (listeners.isEmpty()) {
                listenerMap.remove(packetClass.getName());
            }
        }
    }

    /**
     * 注销某个包的所有 Listener。
     */
    public <T extends IMinecraftPacket> void unregisterAllListeners(Class<T> packetClass) {
        listenerMap.remove(packetClass.getName());
    }

    @SuppressWarnings("unchecked")
    public void dispatch(IMinecraftPacket packet, ChannelHandlerContext ctx, StarlightProxy proxy) {
        Map<String, IPacketListener<?>> listeners = listenerMap.get(packet.getClass().getName());
        if (listeners == null || listeners.isEmpty()) return;
        for (IPacketListener<?> listener : listeners.values()) {
            ((IPacketListener<IMinecraftPacket>) listener).handle(packet, ctx, proxy);
        }
    }
}

