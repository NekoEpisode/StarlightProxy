import io.slidermc.starlight.permission.SimplePermissionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class PermissionServiceTest {

    private ScheduledExecutorService exec;

    @BeforeEach
    void setUp() {
        exec = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        exec.shutdownNow();
    }

    @Test
    void testWildcardExactMatch() {
        Set<String> perms = ConcurrentHashMap.newKeySet();
        perms.add("command.server");
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.server"));
        assertFalse(SimplePermissionManager.matchWildcard(perms, "command.other"));
    }

    @Test
    void testWildcardStar() {
        Set<String> perms = ConcurrentHashMap.newKeySet();
        perms.add("*");
        assertTrue(SimplePermissionManager.matchWildcard(perms, "anything.at.all"));
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.server"));
    }

    @Test
    void testWildcardPrefix() {
        Set<String> perms = ConcurrentHashMap.newKeySet();
        perms.add("command.*");
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.server"));
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.perm.add"));
        assertFalse(SimplePermissionManager.matchWildcard(perms, "other.stuff"));
    }

    @Test
    void testWildcardExplicitDeny() {
        Set<String> perms = ConcurrentHashMap.newKeySet();
        perms.add("command.*");
        perms.add("-command.admin");
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.server"));
        assertTrue(SimplePermissionManager.matchWildcard(perms, "command.perm"));
        assertFalse(SimplePermissionManager.matchWildcard(perms, "command.admin"));
    }

    @Test
    void testEmptyPermissions() {
        Set<String> perms = ConcurrentHashMap.newKeySet();
        assertFalse(SimplePermissionManager.matchWildcard(perms, "anything"));
    }

    @Test
    void testNullPermissions() {
        assertFalse(SimplePermissionManager.matchWildcard(null, "anything"));
    }

    @Test
    void testSimplePermissionManagerSetAndCheck(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("permissions.yml");
        Files.writeString(permFile, """
                permissions:
                  \
                "00000000-0000-0000-0000-000000000001":
                    - command.server
                    - command.perm.*""");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(mgr.hasPermission(uuid, "command.server"));
        assertTrue(mgr.hasPermission(uuid, "command.perm.add"));
        assertFalse(mgr.hasPermission(uuid, "command.admin"));

        mgr.setPermission(uuid, "command.admin", true);
        assertTrue(mgr.hasPermission(uuid, "command.admin"));

        mgr.removePermission(uuid, "command.admin");
        assertFalse(mgr.hasPermission(uuid, "command.admin"));
    }

    @Test
    void testSimplePermissionManagerEmptyFile(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("empty.yml");
        Files.writeString(permFile, "permissions: {}");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.randomUUID();
        assertFalse(mgr.hasPermission(uuid, "anything"));
    }

    @Test
    void testSimplePermissionManagerMissingFile(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("nonexistent.yml");
        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.randomUUID();
        assertFalse(mgr.hasPermission(uuid, "anything"));
    }

    @Test
    void testSimplePermissionManagerReload(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("reload.yml");
        Files.writeString(permFile, """
                permissions:
                  \
                "00000000-0000-0000-0000-000000000001":
                    - test.perm""");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(mgr.hasPermission(uuid, "test.perm"));

        Files.writeString(permFile, """
                permissions:
                  \
                "00000000-0000-0000-0000-000000000001":
                    - test.other""");

        mgr.reload();
        assertFalse(mgr.hasPermission(uuid, "test.perm"));
        assertTrue(mgr.hasPermission(uuid, "test.other"));
    }

    @Test
    void testGetPermissions() {
        SimplePermissionManager mgr = new SimplePermissionManager(Path.of("dummy"), exec);
        UUID uuid = UUID.randomUUID();

        mgr.setPermission(uuid, "foo.bar", true);
        mgr.setPermission(uuid, "baz.qux", true);

        Set<String> perms = mgr.getPermissions(uuid);
        assertEquals(2, perms.size());
        assertTrue(perms.contains("foo.bar"));
        assertTrue(perms.contains("baz.qux"));
    }

    @Test
    void testGetPermissionsEmpty() {
        SimplePermissionManager mgr = new SimplePermissionManager(Path.of("dummy"), exec);
        UUID uuid = UUID.randomUUID();

        assertTrue(mgr.getPermissions(uuid).isEmpty());
    }

    @Test
    void testSaveAndReloadRoundtrip(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("roundtrip.yml");
        UUID uuid = UUID.randomUUID();

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.setPermission(uuid, "test.one", true);
        mgr.setPermission(uuid, "test.two", true);
        mgr.stop();

        SimplePermissionManager mgr2 = new SimplePermissionManager(permFile, exec);
        mgr2.load();
        assertTrue(mgr2.hasPermission(uuid, "test.one"));
        assertTrue(mgr2.hasPermission(uuid, "test.two"));
        assertFalse(mgr2.hasPermission(uuid, "test.three"));
    }

    @Test
    void testSaveRemoveAndReload(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("remove.yml");
        UUID uuid = UUID.randomUUID();

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.setPermission(uuid, "test.keep", true);
        mgr.setPermission(uuid, "test.remove", true);
        mgr.stop();

        SimplePermissionManager mgr2 = new SimplePermissionManager(permFile, exec);
        mgr2.load();
        mgr2.removePermission(uuid, "test.remove");
        mgr2.stop();

        SimplePermissionManager mgr3 = new SimplePermissionManager(permFile, exec);
        mgr3.load();
        assertTrue(mgr3.hasPermission(uuid, "test.keep"));
        assertFalse(mgr3.hasPermission(uuid, "test.remove"));
    }

    @Test
    void testPeriodicFlush(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path permFile = tempDir.resolve("periodic.yml");
        UUID uuid = UUID.randomUUID();

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();
        mgr.start();
        mgr.setPermission(uuid, "test.periodic", true);

        Thread.sleep(1500);

        SimplePermissionManager mgr2 = new SimplePermissionManager(permFile, exec);
        mgr2.load();
        assertTrue(mgr2.hasPermission(uuid, "test.periodic"));

        mgr.stop();
    }

    @Test
    void testInvalidUuidKey(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("invalid_uuid.yml");
        Files.writeString(permFile, """
                permissions:
                  "not-a-valid-uuid":
                    - command.server
                  "00000000-0000-0000-0000-000000000001":
                    - command.test""");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(mgr.hasPermission(uuid, "command.test"));
        assertFalse(mgr.hasPermission(uuid, "command.server"));
    }

    @Test
    void testNonListPermissionsValue(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("nonlist.yml");
        Files.writeString(permFile, """
                permissions:
                  "00000000-0000-0000-0000-000000000001": "just a string"
                  "00000000-0000-0000-0000-000000000002":
                    - command.test""");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertFalse(mgr.hasPermission(uuid1, "anything"));
        assertTrue(mgr.hasPermission(uuid2, "command.test"));
    }

    @Test
    void testNullValuesInPermissionList(@TempDir Path tempDir) throws IOException {
        Path permFile = tempDir.resolve("null_values.yml");
        Files.writeString(permFile, """
                permissions:
                  "00000000-0000-0000-0000-000000000001":
                    - command.one
                    -
                    - command.two""");

        SimplePermissionManager mgr = new SimplePermissionManager(permFile, exec);
        mgr.load();

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(mgr.hasPermission(uuid, "command.one"));
        assertTrue(mgr.hasPermission(uuid, "command.two"));
    }
}
