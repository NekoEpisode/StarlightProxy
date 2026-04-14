package io.slidermc.starlight.network.protocolenum;

/**
 * Represents a version of the Minecraft protocol, encapsulating the specific
 * protocol version code used for communication between the client and server.
 * This enum allows for easy comparison between different protocol versions.
 */
public enum ProtocolVersion {
    MINECRAFT_26_1(775),

    UNKNOWN_OR_PLACEHOLDER(-1);

    private final int protocolVersionCode;

    ProtocolVersion(int protocolVersionCode) {
        this.protocolVersionCode = protocolVersionCode;
    }

    public boolean isGreaterThan(ProtocolVersion other) {
        return protocolVersionCode > other.protocolVersionCode;
    }

    public boolean isLessThan(ProtocolVersion other) {
        return protocolVersionCode < other.protocolVersionCode;
    }

    public boolean isEqual(ProtocolVersion other) {
        return protocolVersionCode == other.protocolVersionCode;
    }

    public boolean isGreaterThanOrEqual(ProtocolVersion other) {
        return protocolVersionCode >= other.protocolVersionCode;
    }

    public boolean isLessThanOrEqual(ProtocolVersion other) {
        return protocolVersionCode <= other.protocolVersionCode;
    }

    public boolean isNotEqual(ProtocolVersion other) {
        return protocolVersionCode != other.protocolVersionCode;
    }

    public int getProtocolVersionCode() {
        return protocolVersionCode;
    }

    public static ProtocolVersion getByProtocolVersionCode(int code) {
        for (ProtocolVersion version : values()) {
            if (version.protocolVersionCode == code) {
                return version;
            }
        }
        return null;
    }
}
