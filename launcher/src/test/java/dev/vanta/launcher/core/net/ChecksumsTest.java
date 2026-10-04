package dev.vanta.launcher.core.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumsTest {

    @TempDir
    Path tmp;

    @Test
    void knownDigests() throws IOException {
        final Path f = tmp.resolve("abc.txt");
        Files.writeString(f, "abc", StandardCharsets.UTF_8);
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Checksums.sha1Hex(f));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256Hex(f));
        assertTrue(Checksum.sha1("A9993E364706816ABA3E25717850C26C9CD0D89D").matches(f));
        assertFalse(Checksum.sha256("0000000000000000000000000000000000000000000000000000000000000000").matches(f));
    }

    @Test
    void checksumValidation() {
        assertThrows(IllegalArgumentException.class, () -> Checksum.sha1("abc"));
        assertThrows(IllegalArgumentException.class, () -> Checksum.sha256("zz".repeat(32)));
        assertEquals("sha1:a9993e364706816aba3e25717850c26c9cd0d89d", Checksum.sha1("a9993e364706816aba3e25717850c26c9cd0d89d").toString());
        assertEquals(40, HashAlgorithm.SHA1.hexLength());
        assertEquals(64, HashAlgorithm.SHA256.hexLength());
    }
}
