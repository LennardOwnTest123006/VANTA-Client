package dev.vanta.launcher.core.auth;

import dev.vanta.launcher.core.util.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM with a random key stored in {@code key.bin} next to the account store, readable only by the owner.
 *
 * <p>Why: on Linux and macOS there is no DPAPI equivalent that works everywhere without a desktop keyring
 * daemon. A per-user random key with owner-only permissions gives the same protection level as the user's home
 * directory (which is also where browsers keep their tokens) while keeping tokens unreadable in backups or
 * copies of the file alone. The key never leaves the machine.</p>
 *
 * <p>Format: {@code 12-byte IV || ciphertext || 16-byte tag}.</p>
 */
public final class AesGcmCrypto implements AccountCrypto {

    /** Identifier written into the store header. */
    public static final String ID = "aes-256-gcm";

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param keyFile key file ({@code key.bin}); created on first use
     */
    public AesGcmCrypto(final Path keyFile) {
        this.keyFile = Objects.requireNonNull(keyFile, "keyFile");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public byte[] encrypt(final byte[] plain) throws GeneralSecurityException, IOException {
        final SecretKey key = loadOrCreateKey();
        final byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        final byte[] encrypted = cipher.doFinal(plain);
        final byte[] out = new byte[IV_BYTES + encrypted.length];
        System.arraycopy(iv, 0, out, 0, IV_BYTES);
        System.arraycopy(encrypted, 0, out, IV_BYTES, encrypted.length);
        return out;
    }

    @Override
    public byte[] decrypt(final byte[] data) throws GeneralSecurityException, IOException {
        if (data.length < IV_BYTES + TAG_BITS / 8) {
            throw new GeneralSecurityException("Ciphertext too short");
        }
        final SecretKey key = loadOrCreateKey();
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
        return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
    }

    /** @return the key file */
    public Path keyFile() {
        return keyFile;
    }

    private SecretKey loadOrCreateKey() throws IOException {
        if (Files.isRegularFile(keyFile)) {
            final byte[] raw = Files.readAllBytes(keyFile);
            if (raw.length == KEY_BYTES) {
                return new SecretKeySpec(raw, "AES");
            }
            throw new IOException("Corrupt key file " + keyFile + " (" + raw.length + " bytes)");
        }
        final byte[] raw = new byte[KEY_BYTES];
        random.nextBytes(raw);
        AtomicFiles.write(keyFile, raw);
        AtomicFiles.restrictToOwner(keyFile);
        return new SecretKeySpec(raw, "AES");
    }
}
