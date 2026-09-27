package io.slidermc.starlight.api.channel;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelPayloadTest {

    @Test
    void parsesNullSeparatedChannels() {
        Set<Key> channels = ChannelPayload.parse(bytes("fastlogin:succ\0fastlogin:ch-st"));

        assertEquals(Set.of(Key.key("fastlogin:succ"), Key.key("fastlogin:ch-st")), channels);
    }

    @Test
    void emptyPayloadYieldsNoChannels() {
        assertTrue(ChannelPayload.parse(new byte[0]).isEmpty());
        assertTrue(ChannelPayload.parse(null).isEmpty());
    }

    @Test
    void trailingSeparatorDoesNotProduceAnEmptyChannel() {
        Set<Key> channels = ChannelPayload.parse(bytes("fastlogin:succ\0"));

        assertEquals(Set.of(Key.key("fastlogin:succ")), channels);
    }

    @Test
    void bareNameDefaultsToTheMinecraftNamespace() {
        Set<Key> channels = ChannelPayload.parse(bytes("brand"));

        assertEquals(Set.of(Key.key("minecraft:brand")), channels);
    }

    @Test
    void skipsInvalidNamesButKeepsTheValidOnes() {
        Set<Key> channels = ChannelPayload.parse(bytes("fastlogin:succ\0NOT VALID!\0fastlogin:ch-st"));

        assertEquals(Set.of(Key.key("fastlogin:succ"), Key.key("fastlogin:ch-st")), channels);
    }

    @Test
    void preservesDeclarationOrder() {
        Set<Key> channels = ChannelPayload.parse(bytes("fastlogin:ch-st\0fastlogin:succ\0other:channel"));

        assertEquals(
                java.util.List.of(Key.key("fastlogin:ch-st"), Key.key("fastlogin:succ"), Key.key("other:channel")),
                java.util.List.copyOf(channels));
    }

    @Test
    void writesBackWhatItParsed() {
        Set<Key> channels = new LinkedHashSet<>(
                java.util.List.of(Key.key("fastlogin:succ"), Key.key("other:channel")));

        byte[] payload = ChannelPayload.write(channels);

        assertArrayEquals(bytes("fastlogin:succ\0other:channel"), payload);
        assertEquals(channels, ChannelPayload.parse(payload));
    }

    @Test
    void refusesToWriteAnEmptySet() {
        assertThrows(IllegalArgumentException.class, () -> ChannelPayload.write(Set.of()));
    }

    @Test
    void roundTripsAChannelSet() {
        Set<Key> channels = Set.of(Key.key("plugin", "chan"));

        assertEquals(channels, ChannelPayload.parse(ChannelPayload.write(channels)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
