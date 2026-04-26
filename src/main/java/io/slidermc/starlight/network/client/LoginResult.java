package io.slidermc.starlight.network.client;

import net.kyori.adventure.text.Component;

/**
 * Sealed type representing the result of an asynchronous downstream login attempt.
 * Callers pattern-match on the three cases to handle success, kick, and error uniformly.
 */
public sealed interface LoginResult {

    /** Login succeeded; the downstream is now in CONFIGURATION state. */
    record Success() implements LoginResult {}

    /**
     * The downstream server rejected the login with a disconnect reason.
     * This is normal protocol behavior, not an I/O error.
     */
    record Kicked(Component reason) implements LoginResult {}

    /** The login failed due to an I/O error or unexpected condition. */
    record Error(Throwable cause) implements LoginResult {}
}
