package dev.vanta.launcher.core.auth;

import java.security.GeneralSecurityException;

/**
 * Windows Data Protection API (user scope) via JNA's {@code Crypt32Util}. Data encrypted this way can only be
 * decrypted by the same Windows user on the same machine, without any key file to manage. JNA classes are only
 * touched inside the methods so the class loads on every platform.
 */
public final class DpapiCrypto implements AccountCrypto {

    /** Identifier written into the store header. */
    public static final String ID = "dpapi";

    private static final byte[] ENTROPY = "VANTA Launcher accounts v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public byte[] encrypt(final byte[] plain) throws GeneralSecurityException {
        try {
            return com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(plain, ENTROPY, 0, "VANTA Launcher", null);
        } catch (RuntimeException | LinkageError e) {
            throw new GeneralSecurityException("DPAPI encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(final byte[] cipher) throws GeneralSecurityException {
        try {
            return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(cipher, ENTROPY, 0, null);
        } catch (RuntimeException | LinkageError e) {
            throw new GeneralSecurityException("DPAPI decryption failed (different user or machine?)", e);
        }
    }
}
