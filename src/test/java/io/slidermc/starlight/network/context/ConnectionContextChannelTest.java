package io.slidermc.starlight.network.context;

import io.slidermc.starlight.StarlightProxy;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionContextChannelTest {

    private static final Key PROXY_CHANNEL = Key.key("fastlogin:ch-st");
    private static final Key CLIENT_CHANNEL = Key.key("jade:client_handshake");

    private ConnectionContext newContext() {
        return new ConnectionContext(null, null);
    }

    @Test
    void clientChannelsStartEmpty() {
        ConnectionContext context = newContext();

        assertTrue(context.getClientChannels().isEmpty());
        assertEquals(0, context.getClientChannelCount());
    }

    @Test
    void addAndRemoveClientChannel() {
        ConnectionContext context = newContext();

        assertTrue(context.addClientChannel(CLIENT_CHANNEL));
        assertFalse(context.addClientChannel(CLIENT_CHANNEL), "重复添加应返回 false");
        assertEquals(1, context.getClientChannelCount());

        assertTrue(context.removeClientChannel(CLIENT_CHANNEL));
        assertFalse(context.removeClientChannel(CLIENT_CHANNEL), "重复移除应返回 false");
        assertTrue(context.getClientChannels().isEmpty());
    }

    @Test
    void clientChannelsSnapshotIsIndependent() {
        ConnectionContext context = newContext();
        context.addClientChannel(CLIENT_CHANNEL);

        Set<Key> snapshot = context.getClientChannels();
        context.addClientChannel(PROXY_CHANNEL);

        assertFalse(snapshot.contains(PROXY_CHANNEL));
    }

    @Test
    void everythingIsPendingBeforeTheFirstAnnouncement() {
        ConnectionContext context = newContext();

        Set<Key> pending = context.diffAnnounced(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL));

        assertEquals(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL), pending);
    }

    @Test
    void nothingIsPendingAfterAnnouncingTheSameSet() {
        ConnectionContext context = newContext();
        Set<Key> channels = Set.of(PROXY_CHANNEL, CLIENT_CHANNEL);

        context.setAnnouncedChannels(channels);

        assertTrue(context.diffAnnounced(channels).isEmpty());
    }

    @Test
    void onlyNewlyRegisteredChannelsArePending() {
        ConnectionContext context = newContext();
        context.setAnnouncedChannels(Set.of(PROXY_CHANNEL));

        Set<Key> pending = context.diffAnnounced(new LinkedHashSet<>(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL)));

        assertEquals(Set.of(CLIENT_CHANNEL), pending);
    }

    @Test
    void settingAnnouncedChannelsReplacesThePreviousSet() {
        ConnectionContext context = newContext();
        context.setAnnouncedChannels(Set.of(PROXY_CHANNEL));

        context.setAnnouncedChannels(Set.of(CLIENT_CHANNEL));

        assertEquals(Set.of(PROXY_CHANNEL), context.diffAnnounced(Set.of(PROXY_CHANNEL)));
    }

    @Test
    void clearingDownstreamMakesEverythingPendingAgain() {
        ConnectionContext context = newContext();
        context.setAnnouncedChannels(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL));

        // 切换下游时 setDownstreamChannel 会清空已下发状态
        context.setDownstreamChannel(null);

        assertEquals(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL),
                context.diffAnnounced(Set.of(PROXY_CHANNEL, CLIENT_CHANNEL)));
    }
}
