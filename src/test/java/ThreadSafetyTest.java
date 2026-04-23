import io.slidermc.starlight.api.command.CommandManager;
import io.slidermc.starlight.api.event.EventHandler;
import io.slidermc.starlight.api.event.EventManager;
import io.slidermc.starlight.api.event.IStarlightEvent;
import io.slidermc.starlight.api.event.events.internal.PlayerChatEvent;
import io.slidermc.starlight.api.event.events.internal.ReceivePluginMessageEvent;
import io.slidermc.starlight.api.player.PlayerManager;
import io.slidermc.starlight.api.player.ProxiedPlayer;
import io.slidermc.starlight.api.profile.GameProfile;
import io.slidermc.starlight.api.translate.TranslateManager;
import io.slidermc.starlight.executor.ProxyExecutors;
import io.slidermc.starlight.network.client.StarlightMinecraftClient;
import io.slidermc.starlight.network.context.ConnectionContext;
import io.slidermc.starlight.network.context.DownstreamConnectionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程安全相关的单元测试，验证 EventManager 并发注册/注销、PlayerManager 并发增删等场景。
 */
public class ThreadSafetyTest {

    private EventManager manager;
    private ProxyExecutors proxyExecutors;

    static class SimpleEvent implements IStarlightEvent {}

    /** 消除 EventListener 歧义的辅助基类。 */
    static abstract class BaseListener implements io.slidermc.starlight.api.event.EventListener {}

    @BeforeEach
    public void setUp() {
        proxyExecutors = new ProxyExecutors();
        manager = new EventManager(
                proxyExecutors.getEventExecutor(),
                new TranslateManager()
        );
    }

    @AfterEach
    public void cleanUp() {
        proxyExecutors.shutdown();
    }

    @Test
    public void testConcurrentRegisterNoDuplicateIds() throws Exception {
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    manager.register("listener-" + idx, new BaseListener() {
                        @EventHandler
                        public void onSimple(SimpleEvent e) {}
                    });
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    duplicateCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get(), "所有不同 ID 的注册都应成功");
        assertEquals(0, duplicateCount.get(), "不应出现重复 ID 异常");
        assertEquals(threadCount, manager.getHandlerCount(SimpleEvent.class));
    }

    @Test
    public void testConcurrentRegisterSameIdOnlyOneSucceeds() throws Exception {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    manager.register("same-id", new BaseListener() {
                        @EventHandler
                        public void onSimple(SimpleEvent e) {}
                    });
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException ignored) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        assertEquals(1, successCount.get(), "相同 ID 的注册应只有一个成功");
    }

    @Test
    public void testConcurrentFireAsyncWhileRegistering() throws Exception {
        int fireCount = 100;
        int registerCount = 20;

        manager.register("base", new BaseListener() {
            @EventHandler
            public void onSimple(SimpleEvent e) {}
        });

        CountDownLatch fireLatch = new CountDownLatch(fireCount);
        CountDownLatch registerLatch = new CountDownLatch(registerCount);
        AtomicInteger firedCount = new AtomicInteger(0);

        ExecutorService fireExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("fire-", 0).factory()
        );

        for (int i = 0; i < fireCount; i++) {
            fireExecutor.submit(() -> {
                manager.fireAsync(new SimpleEvent()).whenComplete((e, t) -> {
                    if (t == null) firedCount.incrementAndGet();
                    fireLatch.countDown();
                });
            });
        }

        for (int i = 0; i < registerCount; i++) {
            final int idx = i;
            Thread.startVirtualThread(() -> {
                try {
                    manager.register("concurrent-" + idx, new BaseListener() {
                        @EventHandler
                        public void onSimple(SimpleEvent e) {}
                    });
                } catch (Exception ignored) {
                } finally {
                    registerLatch.countDown();
                }
            });
        }

        assertTrue(fireLatch.await(10, TimeUnit.SECONDS));
        assertTrue(registerLatch.await(10, TimeUnit.SECONDS));
        assertEquals(fireCount, firedCount.get(), "所有 fireAsync 应正常完成");

        fireExecutor.shutdown();
    }

    @Test
    public void testPlayerManagerConcurrentAddRemove() throws Exception {
        PlayerManager playerManager = new PlayerManager();
        int playerCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(playerCount * 2);

        List<ProxiedPlayer> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            UUID uuid = UUID.randomUUID();
            String name = "player" + i;
            players.add(new ProxiedPlayer(
                    new GameProfile(name, uuid, List.of()), null, null, true
            ));
        }

        for (int i = 0; i < playerCount; i++) {
            final int idx = i;
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    playerManager.addPlayer(players.get(idx));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    playerManager.removePlayer(players.get(idx).getGameProfile().uuid());
                } catch (NullPointerException e) {
                    fail("removePlayer should not throw NPE");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        for (ProxiedPlayer p : players) {
            ProxiedPlayer byUuid = playerManager.getPlayer(p.getGameProfile().uuid());
            ProxiedPlayer byName = playerManager.getPlayer(p.getGameProfile().username());
            assertEquals(byUuid == null, byName == null,
                    "UUID 和 name 索引应一致: " + p.getGameProfile().username());
        }
    }

    @Test
    public void testTranslateManagerConcurrentReadWrite() throws Exception {
        TranslateManager tm = new TranslateManager();
        tm.loadBuiltin();

        int readerCount = 50;
        int writerCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerCount + writerCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < readerCount; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 200; j++) {
                        tm.translate("starlight.startup");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (int i = 0; i < writerCount; i++) {
            final int idx = i;
            Thread.startVirtualThread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 20; j++) {
                        tm.addTranslation("test_locale_" + idx, "key" + j, "value" + j);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "应在超时内完成");
        assertEquals(0, errors.get(), "不应有并发读写异常");
    }

    @Test
    public void testConcurrentUnregisterAll() throws Exception {
        int listenerCount = 30;
        String pluginId = "test-plugin";

        io.slidermc.starlight.api.plugin.IPlugin plugin = new io.slidermc.starlight.api.plugin.PluginBase(
                io.slidermc.starlight.api.plugin.PluginDescription.memory(pluginId, "1.0")
        ) {};

        for (int idx = 0; idx < listenerCount; idx++) {
            manager.register(plugin, "listener-" + idx, new BaseListener() {
                @EventHandler
                public void onSimple(SimpleEvent e) {}
            });
        }

        assertEquals(listenerCount, manager.getHandlerCount(SimpleEvent.class));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger errors = new AtomicInteger(0);

        Thread.startVirtualThread(() -> {
            try {
                startLatch.await();
                manager.unregisterAll(plugin);
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread.startVirtualThread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 50; i++) {
                    manager.fireAsync(new SimpleEvent()).get(1, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get(), "不应有异常");
        assertEquals(0, manager.getHandlerCount(SimpleEvent.class), "所有 handler 应被注销");
    }

    @Test
    public void testPluginManagerUsesCopyOnWriteArrayList() throws Exception {
        var field = io.slidermc.starlight.plugin.PluginManager.class.getDeclaredField("orderedPlugins");
        field.setAccessible(true);
        Object instance = new io.slidermc.starlight.plugin.PluginManager(new TranslateManager());
        Object value = field.get(instance);
        assertInstanceOf(CopyOnWriteArrayList.class, value, "PluginManager.orderedPlugins 实际类型应为 CopyOnWriteArrayList，但为 " + value.getClass().getName());

        var pendingField = io.slidermc.starlight.plugin.PluginManager.class.getDeclaredField("pendingMemoryPlugins");
        pendingField.setAccessible(true);
        Object pendingValue = pendingField.get(instance);
        assertInstanceOf(CopyOnWriteArrayList.class, pendingValue, "PluginManager.pendingMemoryPlugins 实际类型应为 CopyOnWriteArrayList，但为 " + pendingValue.getClass().getName());
    }

    @Test
    public void testConnectionContextVolatileFields() throws Exception {
        Class<?> clazz = ConnectionContext.class;
        String[] volatileFields = {
                "handshakeInformation", "inboundState", "outboundState",
                "player", "downstreamChannel", "clientInformation",
                "verifyToken", "pendingUsername"
        };
        for (String fieldName : volatileFields) {
            var field = clazz.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()), fieldName + " 应为 volatile");
        }
    }

    @Test
    public void testDownstreamConnectionContextVolatileClient() throws Exception {
        var field = DownstreamConnectionContext.class.getDeclaredField("client");
        assertTrue(Modifier.isVolatile(field.getModifiers()), "DownstreamConnectionContext.client 应为 volatile");
    }

    @Test
    public void testStarlightMinecraftClientVolatileFields() throws Exception {
        Class<?> clazz = StarlightMinecraftClient.class;
        for (String fieldName : new String[]{"protocolVersion", "outboundState", "inboundState"}) {
            var field = clazz.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()), fieldName + " 应为 volatile");
        }
    }

    @Test
    public void testReceivePluginMessageEventVolatileFields() throws Exception {
        Class<?> clazz = ReceivePluginMessageEvent.class;
        for (String fieldName : new String[]{"key", "data", "result"}) {
            var field = clazz.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()), fieldName + " 应为 volatile");
        }
    }

    @Test
    public void testPlayerChatEventVolatileFields() throws Exception {
        Class<?> clazz = PlayerChatEvent.class;
        for (String fieldName : new String[]{"cancelled", "message"}) {
            var field = clazz.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()), fieldName + " 应为 volatile");
        }
    }

    @Test
    public void testEventManagerHasRegistryLock() throws Exception {
        var field = EventManager.class.getDeclaredField("registryLock");
        assertEquals(java.util.concurrent.locks.ReentrantLock.class, field.getType(),
                "EventManager 应有 ReentrantLock registryLock");
    }

    @Test
    public void testCommandManagerHasLock() throws Exception {
        var field = CommandManager.class.getDeclaredField("lock");
        assertEquals(Object.class, field.getType(), "CommandManager 应有 Object lock");
    }

    @Test
    public void testTranslateManagerActiveLocaleVolatile() throws Exception {
        var field = TranslateManager.class.getDeclaredField("activeLocale");
        assertTrue(Modifier.isVolatile(field.getModifiers()), "TranslateManager.activeLocale 应为 volatile");
    }

    @Test
    public void testTranslateManagerUsesConcurrentHashMap() throws Exception {
        var field = TranslateManager.class.getDeclaredField("languages");
        TranslateManager instance = new TranslateManager();
        field.setAccessible(true);
        Object value = field.get(instance);
        assertInstanceOf(ConcurrentHashMap.class, value, "TranslateManager.languages 实际类型应为 ConcurrentHashMap，但为 " + value.getClass().getName());
    }
}
