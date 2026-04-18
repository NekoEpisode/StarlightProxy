package io.slidermc.starlight.switcher;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Thrown when the target downstream server rejects the player during a server switch (login kick).
 * Carries the original kick reason so it can be shown to the player.
 */
public class ServerSwitchKickedException extends RuntimeException {
    private final Component reason;

    public ServerSwitchKickedException(Component reason) {
        super(PlainTextComponentSerializer.plainText().serialize(reason));
        this.reason = reason;
    }

    public Component getReason() {
        return reason;
    }
}

