package io.slidermc.starlight.api.channel;

import io.slidermc.starlight.api.translate.TranslateManager;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelRegistryTest {

    private static final Key SUCCESS = Key.key("fastlogin:succ");
    private static final Key CHANGE = Key.key("fastlogin:ch-st");

    private ChannelRegistry registry;

    @BeforeEach
    void setUp() {
        TranslateManager translateManager = new TranslateManager();
        translateManager.loadBuiltin();
        registry = new ChannelRegistry(translateManager);
    }

    @Test
    void startsEmpty() {
        assertTrue(registry.getChannels().isEmpty());
        assertFalse(registry.isRegistered(SUCCESS));
    }

    @Test
    void registersChannels() {
        registry.register(SUCCESS, CHANGE);

        assertEquals(Set.of(SUCCESS, CHANGE), registry.getChannels());
        assertTrue(registry.isRegistered(SUCCESS));
    }

    @Test
    void registeringTwiceKeepsASingleEntry() {
        registry.register(SUCCESS);
        registry.register(SUCCESS);

        assertEquals(1, registry.getChannels().size());
    }

    @Test
    void unregistersSingleChannel() {
        registry.register(SUCCESS, CHANGE);

        registry.unregister(SUCCESS);

        assertEquals(Set.of(CHANGE), registry.getChannels());
    }

    @Test
    void unregisterAllOnlyAffectsTheGivenNamespace() {
        Key other = Key.key("other:channel");
        registry.register(SUCCESS, CHANGE, other);

        registry.unregisterAll("fastlogin");

        assertEquals(Set.of(other), registry.getChannels());
    }

    @Test
    void unregisterAllOfUnknownNamespaceIsANoOp() {
        registry.register(SUCCESS);

        registry.unregisterAll("not-installed");

        assertEquals(Set.of(SUCCESS), registry.getChannels());
    }

    @Test
    void rejectsReservedNamespace() {
        Key reserved = Key.key("minecraft:register");

        assertThrows(IllegalArgumentException.class, () -> registry.register(reserved));
        assertTrue(registry.getChannels().isEmpty());
    }

    @Test
    void rejectsBatchContainingAReservedChannel() {
        Key reserved = Key.key("minecraft:brand");

        assertThrows(IllegalArgumentException.class, () -> registry.register(SUCCESS, reserved));
        // 校验先于写入，因此整批都不应被注册
        assertTrue(registry.getChannels().isEmpty());
    }

    @Test
    void snapshotIsNotBackedByTheRegistry() {
        registry.register(SUCCESS);
        Set<Key> snapshot = registry.getChannels();

        registry.register(CHANGE);

        assertFalse(snapshot.contains(CHANGE), "快照不应随后续注册变化");
    }

    @Test
    void concurrentRegistrationsDoNotLoseChannels() throws Exception {
        int threads = 8;
        int perThread = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        try {
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            registry.register(Key.key("plugin" + threadIndex, "ch" + i));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发注册未在超时内完成");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads * perThread, registry.getChannels().size());
    }
}
