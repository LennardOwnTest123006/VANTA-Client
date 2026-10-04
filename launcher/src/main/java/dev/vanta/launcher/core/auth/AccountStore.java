package dev.vanta.launcher.core.auth;

import com.google.gson.JsonParseException;
import dev.vanta.launcher.core.log.LauncherLog;
import dev.vanta.launcher.core.paths.LauncherPaths;
import dev.vanta.launcher.core.util.AtomicFiles;
import dev.vanta.launcher.core.util.Json;
import dev.vanta.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Encrypted persistence of signed-in accounts ({@code accounts.dat}).
 *
 * <p>The file is a small JSON envelope {@code {"schemaVersion":1,"cipher":"dpapi|aes-256-gcm","payload":"base64"}}
 * whose payload is the encrypted account list. Windows uses DPAPI (user scope); other platforms use AES-256-GCM
 * with an owner-only key file (see {@link AesGcmCrypto} for the reasoning). OAuth never involves passwords, so
 * none are stored — only tokens, which are registered for log redaction on load.</p>
 */
public final class AccountStore {

    /** Current schema version of the envelope and payload. */
    public static final int SCHEMA_VERSION = 1;

    private static final Logger LOG = LauncherLog.get("Accounts");

    private final Path file;
    private final AccountCrypto crypto;
    private final List<Account> accounts = new ArrayList<>();
    private String activeUuid = "";
    private boolean loaded;

    /**
     * @param file   {@code accounts.dat}
     * @param crypto cipher
     */
    public AccountStore(final Path file, final AccountCrypto crypto) {
        this.file = Objects.requireNonNull(file, "file");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    /**
     * Opens the store with the platform cipher.
     *
     * @param paths launcher paths
     * @param os    host platform
     * @return store (not yet loaded)
     */
    public static AccountStore open(final LauncherPaths paths, final OsInfo os) {
        final AccountCrypto crypto = os.isWindows() ? new DpapiCrypto() : new AesGcmCrypto(paths.keyFile());
        return new AccountStore(paths.accountsFile(), crypto);
    }

    /** @return the store file */
    public Path file() {
        return file;
    }

    /** @return cipher id in use */
    public String cipherId() {
        return crypto.id();
    }

    /**
     * Loads the store. A missing file yields an empty store; an undecryptable file is renamed to
     * {@code accounts.dat.unreadable} and the user has to sign in again (tokens are never recoverable without the key).
     *
     * @return this
     * @throws IOException on I/O failure
     */
    public synchronized AccountStore load() throws IOException {
        accounts.clear();
        activeUuid = "";
        loaded = true;
        if (!Files.isRegularFile(file)) {
            return this;
        }
        try {
            final Envelope envelope = Json.parse(Files.readString(file, StandardCharsets.UTF_8), Envelope.class);
            if (!crypto.id().equals(envelope.cipher())) {
                throw new GeneralSecurityException("accounts.dat was written with cipher '" + envelope.cipher()
                    + "' but this platform uses '" + crypto.id() + "'");
            }
            final byte[] plain = crypto.decrypt(Base64.getDecoder().decode(envelope.payload()));
            final Payload payload = Json.parse(new String(plain, StandardCharsets.UTF_8), Payload.class);
            accounts.addAll(payload.accounts());
            activeUuid = payload.activeUuid() == null ? "" : payload.activeUuid();
            for (Account a : accounts) {
                LauncherLog.redact(a.accessToken());
                LauncherLog.redact(a.refreshToken());
            }
        } catch (GeneralSecurityException | JsonParseException | IllegalArgumentException e) {
            LOG.log(Level.WARNING, "accounts.dat could not be read ({0}); you will need to sign in again", e.toString());
            Files.move(file, file.resolveSibling(file.getFileName() + ".unreadable"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            accounts.clear();
            activeUuid = "";
        }
        return this;
    }

    /**
     * Encrypts and writes the store atomically.
     *
     * @throws IOException on I/O or crypto failure
     */
    public synchronized void save() throws IOException {
        final Payload payload = new Payload(SCHEMA_VERSION, List.copyOf(accounts), activeUuid);
        final byte[] plain = Json.toJson(payload).getBytes(StandardCharsets.UTF_8);
        final byte[] cipher;
        try {
            cipher = crypto.encrypt(plain);
        } catch (GeneralSecurityException e) {
            throw new IOException("Cannot encrypt account store", e);
        }
        final Envelope envelope = new Envelope(SCHEMA_VERSION, crypto.id(), Base64.getEncoder().encodeToString(cipher));
        AtomicFiles.writeString(file, Json.toJson(envelope) + System.lineSeparator());
        AtomicFiles.restrictToOwner(file);
    }

    /** @return all accounts (copy) */
    public synchronized List<Account> accounts() {
        ensureLoaded();
        return List.copyOf(accounts);
    }

    /** @return the active account */
    public synchronized Optional<Account> active() {
        ensureLoaded();
        return accounts.stream().filter(a -> a.uuid().equals(activeUuid)).findFirst()
            .or(() -> accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0)));
    }

    /**
     * @param uuid account uuid
     * @return account when present
     */
    public synchronized Optional<Account> find(final String uuid) {
        ensureLoaded();
        return accounts.stream().filter(a -> a.uuid().equals(uuid)).findFirst();
    }

    /**
     * Adds or replaces an account (matched by uuid and type) and makes it active.
     *
     * @param account account
     */
    public synchronized void put(final Account account) {
        ensureLoaded();
        accounts.removeIf(a -> a.uuid().equals(account.uuid()) && a.type() == account.type());
        accounts.add(0, account);
        activeUuid = account.uuid();
        LauncherLog.redact(account.accessToken());
        LauncherLog.redact(account.refreshToken());
    }

    /**
     * Removes an account.
     *
     * @param uuid account uuid
     * @return whether something was removed
     */
    public synchronized boolean remove(final String uuid) {
        ensureLoaded();
        final boolean removed = accounts.removeIf(a -> a.uuid().equals(uuid));
        if (uuid.equals(activeUuid)) {
            activeUuid = accounts.isEmpty() ? "" : accounts.get(0).uuid();
        }
        return removed;
    }

    /**
     * Marks an account active.
     *
     * @param uuid account uuid
     * @throws IllegalArgumentException when unknown
     */
    public synchronized void setActive(final String uuid) {
        ensureLoaded();
        if (accounts.stream().noneMatch(a -> a.uuid().equals(uuid))) {
            throw new IllegalArgumentException("Unknown account " + uuid);
        }
        activeUuid = uuid;
    }

    /** @return whether a verified Microsoft account is stored */
    public synchronized boolean hasVerifiedMicrosoftAccount() {
        ensureLoaded();
        return accounts.stream().anyMatch(a -> a.type() == AccountType.MICROSOFT);
    }

    /** @return the first verified Microsoft account */
    public synchronized Optional<Account> verifiedMicrosoftAccount() {
        ensureLoaded();
        return active().filter(a -> a.type() == AccountType.MICROSOFT)
            .or(() -> accounts.stream().filter(a -> a.type() == AccountType.MICROSOFT).findFirst());
    }

    private void ensureLoaded() {
        if (!loaded) {
            try {
                load();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Account store unavailable", e);
                loaded = true;
            }
        }
    }

    /**
     * On-disk envelope.
     *
     * @param schemaVersion schema version
     * @param cipher        cipher id
     * @param payload       base64 ciphertext
     */
    record Envelope(int schemaVersion, String cipher, String payload) {
    }

    /**
     * Decrypted payload.
     *
     * @param schemaVersion schema version
     * @param accounts      accounts
     * @param activeUuid    active account uuid
     */
    record Payload(int schemaVersion, List<Account> accounts, String activeUuid) {

        Payload {
            accounts = accounts == null ? List.of() : List.copyOf(accounts);
        }
    }
}
