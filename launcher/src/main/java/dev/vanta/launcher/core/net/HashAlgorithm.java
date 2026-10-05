package dev.vanta.launcher.core.net;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash algorithms used for download verification.
 */
public enum HashAlgorithm {

    /** SHA-1 (Mojang and Maven metadata). */
    SHA1("SHA-1", 40),
    /** SHA-256 (VANTA manifests, Adoptium, Fabric Maven sidecars). */
    SHA256("SHA-256", 64),
    /** SHA-512 (Modrinth version files). */
    SHA512("SHA-512", 128);

    private final String jcaName;
    private final int hexLength;

    HashAlgorithm(final String jcaName, final int hexLength) {
        this.jcaName = jcaName;
        this.hexLength = hexLength;
    }

    /** @return JCA algorithm name */
    public String jcaName() {
        return jcaName;
    }

    /** @return length of a hex digest */
    public int hexLength() {
        return hexLength;
    }

    /** @return a new digest instance */
    public MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(jcaName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(jcaName + " is mandatory in every JRE", e);
        }
    }
}
