package io.slidermc.starlight.api.event.events.helper;

public enum PluginMessageResult {
    HANDLED(0),
    DROPPED(1),
    FORWARD(2),
    NONE(3),
    UNKNOWN(4);

    private final int code;

    PluginMessageResult(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PluginMessageResult fromCode(int code) {
        for (PluginMessageResult result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return UNKNOWN;
    }
}
