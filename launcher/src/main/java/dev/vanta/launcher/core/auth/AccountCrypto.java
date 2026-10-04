package dev.vanta.launcher.core.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Encryption of the account store at rest.
 */
public interface AccountCrypto {

    /** @return short identifier written into the store header ({@code dpapi}, {@code aes-256-gcm}) */
    String id();

    /**
     * @param plain plaintext
     * @return ciphertext
     * @throws GeneralSecurityException on crypto failure
     * @throws IOException              on key storage failure
     */
    byte[] encrypt(byte[] plain) throws GeneralSecurityException, IOException;

    /**
     * @param cipher ciphertext
     * @return plaintext
     * @throws GeneralSecurityException when the data cannot be decrypted (wrong key/user, tampering)
     * @throws IOException              on key storage failure
     */
    byte[] decrypt(byte[] cipher) throws GeneralSecurityException, IOException;
}
