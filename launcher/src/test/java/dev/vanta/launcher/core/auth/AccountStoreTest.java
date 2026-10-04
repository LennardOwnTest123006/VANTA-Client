package dev.vanta.launcher.core.auth;

import dev.vanta.launcher.core.log.Redactor;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.settings.LauncherSettings;
import dev.vanta.launcher.core.util.OsInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {

    @TempDir
    Path tmp;

    private static final Account MS = new Account("3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708", "VantaTester", "2535412345678901",
        "eyJhbGciOiJIUzI1NiJ9.very-secret-access-token.sig", 1_900_000_000_000L, "M.C512_very-secret-refresh-token", AccountType.MICROSOFT);

    @Test
    void aesRoundTripAndFilePermissions() throws IOException {
        final LauncherPaths paths = new LauncherPaths(tmp);
        final AccountStore store = new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile()));
        store.load();
        assertTrue(store.accounts().isEmpty());
        store.put(MS);
        store.save();

        final String onDisk = Files.readString(paths.accountsFile(), StandardCharsets.UTF_8);
        assertTrue(onDisk.contains("\"cipher\": \"aes-256-gcm\""));
        assertFalse(onDisk.contains("very-secret"), "tokens are encrypted at rest");
        assertFalse(onDisk.contains("VantaTester"));
        assertEquals(32, Files.size(paths.keyFile()));
        if (Files.getFileStore(paths.keyFile()).supportsFileAttributeView("posix")) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(paths.keyFile())));
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(paths.accountsFile())));
        }

        final AccountStore reloaded = new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile())).load();
        assertEquals(List.of(MS), reloaded.accounts());
        assertEquals(MS, reloaded.active().orElseThrow());
        assertTrue(reloaded.hasVerifiedMicrosoftAccount());
        assertEquals("aes-256-gcm", reloaded.cipherId());
    }

    @Test
    void loadedTokensAreRedactedInLogs() throws IOException {
        final LauncherPaths paths = new LauncherPaths(tmp);
        final AccountStore store = new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile()));
        store.put(MS);
        store.save();
        new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile())).load();
        final String masked = Redactor.global().apply("a=" + MS.accessToken() + " r=" + MS.refreshToken());
        assertFalse(masked.contains("very-secret"));
    }

    @Test
    void wrongKeyMakesFileUnreadableAndStoreRecovers() throws IOException {
        final LauncherPaths paths = new LauncherPaths(tmp);
        final AccountStore store = new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile()));
        store.put(MS);
        store.save();
        Files.delete(paths.keyFile()); // simulate a lost/rotated key
        final AccountStore reloaded = new AccountStore(paths.accountsFile(), new AesGcmCrypto(paths.keyFile())).load();
        assertTrue(reloaded.accounts().isEmpty());
        assertTrue(Files.exists(paths.accountsFile().resolveSibling("accounts.dat.unreadable")));
        assertFalse(Files.exists(paths.accountsFile()));
    }

    @Test
    void tamperedCiphertextIsRejected() throws Exception {
        final AesGcmCrypto crypto = new AesGcmCrypto(tmp.resolve("key.bin"));
        final byte[] cipher = crypto.encrypt("hello".getBytes(StandardCharsets.UTF_8));
        cipher[cipher.length - 1] ^= 0x01;
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(cipher));
        assertThrows(GeneralSecurityException.class, () -> crypto.decrypt(new byte[5]));
    }

    @Test
    void activeRemoveAndMultipleAccounts() throws IOException {
        final AccountStore store = new AccountStore(tmp.resolve("accounts.dat"), new AesGcmCrypto(tmp.resolve("key.bin"))).load();
        final Account second = new Account("00000000-0000-0000-0000-000000000002", "Other", "", "tok", 1L, "ref", AccountType.MICROSOFT);
        store.put(MS);
        store.put(second);
        assertEquals(second, store.active().orElseThrow(), "last added becomes active");
        store.setActive(MS.uuid());
        assertEquals(MS, store.active().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> store.setActive("nope"));
        assertTrue(store.remove(MS.uuid()));
        assertEquals(second, store.active().orElseThrow());
        assertFalse(store.remove(MS.uuid()));
        store.put(MS.withTokens("new", 2L, "newref"));
        assertEquals("new", store.find(MS.uuid()).orElseThrow().accessToken());
        assertEquals(2, store.accounts().size(), "replaced, not duplicated");
    }

    @Test
    void offlineSessionReusesVerifiedProfile() throws Exception {
        final AccountStore store = new AccountStore(tmp.resolve("accounts.dat"), new AesGcmCrypto(tmp.resolve("key.bin"))).load();
        final LauncherSettings settings = LauncherSettings.defaults(0);
        assertFalse(OfflineAccountPolicy.offlineAllowed(settings, Map.of(), store));
        assertThrows(AuthException.class, () -> OfflineAccountPolicy.createOffline(settings, Map.of(), store, "Anyone"));

        store.put(MS);
        assertTrue(OfflineAccountPolicy.offlineAllowed(settings, Map.of(), store));
        final Account offline = OfflineAccountPolicy.createOffline(settings, Map.of(), store, "Ignored");
        assertEquals(MS.uuid(), offline.uuid());
        assertEquals("VantaTester", offline.name());
        assertEquals(AccountType.OFFLINE, offline.type());
        assertEquals(Account.OFFLINE_TOKEN, offline.accessToken());
        assertEquals("legacy", offline.userType());
        assertFalse(offline.canRefresh());
        assertFalse(offline.isExpired(java.time.Instant.now()));
    }

    @Test
    void developmentOfflineRequiresDeveloperModeAndEnv() throws Exception {
        final AccountStore store = new AccountStore(tmp.resolve("accounts.dat"), new AesGcmCrypto(tmp.resolve("key.bin"))).load();
        final LauncherSettings dev = LauncherSettings.defaults(0).withDeveloperMode(true);
        assertFalse(OfflineAccountPolicy.developmentOfflineEnabled(dev, Map.of()));
        assertFalse(OfflineAccountPolicy.developmentOfflineEnabled(LauncherSettings.defaults(0), Map.of("VANTA_DEV_OFFLINE", "1")));
        assertTrue(OfflineAccountPolicy.developmentOfflineEnabled(dev, Map.of("VANTA_DEV_OFFLINE", "1")));
        final Account account = OfflineAccountPolicy.createOffline(dev, Map.of("VANTA_DEV_OFFLINE", "1"), store, "CI_Player");
        assertEquals("CI_Player", account.name());
        assertEquals(AccountType.DEVELOPMENT, account.type());
        assertEquals(Account.offlineUuid("CI_Player"), account.uuid());
        assertThrows(AuthException.class, () -> OfflineAccountPolicy.createOffline(dev, Map.of("VANTA_DEV_OFFLINE", "1"), store, "bad name!"));
        assertEquals("Dev", OfflineAccountPolicy.createOffline(dev, Map.of("VANTA_DEV_OFFLINE", "1"), store, "").name());
    }

    @Test
    void platformCipherSelection() {
        final LauncherPaths paths = new LauncherPaths(tmp);
        assertEquals("aes-256-gcm", AccountStore.open(paths, new OsInfo("linux", "x64", "")).cipherId());
        assertEquals("aes-256-gcm", AccountStore.open(paths, new OsInfo("osx", "arm64", "")).cipherId());
        assertEquals("dpapi", AccountStore.open(paths, new OsInfo("windows", "x64", "")).cipherId());
    }

    @Test
    void uuidHelpers() {
        assertEquals("3f2a1b4c-5d6e-7f80-91a2-b3c4d5e6f708", Account.dashUuid("3f2a1b4c5d6e7f8091a2b3c4d5e6f708"));
        assertThrows(IllegalArgumentException.class, () -> Account.dashUuid("abc"));
        assertEquals(Account.offlineUuid("Steve"), Account.offlineUuid("Steve"));
        assertTrue(Account.offlineUuid("Steve").matches("[0-9a-f-]{36}"));
    }
}
