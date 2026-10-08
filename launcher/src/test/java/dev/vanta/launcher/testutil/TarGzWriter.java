package dev.vanta.launcher.testutil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal ustar + gzip writer for tests: regular files with a mode (so the executable bit of a fake
 * {@code llama-server} can be asserted after extraction). Nothing else is supported.
 */
public final class TarGzWriter implements AutoCloseable {

    private static final int BLOCK = 512;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final OutputStream out;

    /**
     * @throws IOException never (in-memory)
     */
    public TarGzWriter() throws IOException {
        out = new GZIPOutputStream(bytes);
    }

    /**
     * Adds a regular file.
     *
     * @param name    entry name (forward slashes, may contain directories)
     * @param content content
     * @param mode    permission bits, e.g. {@code 0755}
     * @return this
     * @throws IOException on failure
     */
    public TarGzWriter file(final String name, final byte[] content, final int mode) throws IOException {
        final byte[] header = new byte[BLOCK];
        final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 100) {
            throw new IOException("name too long for this writer: " + name);
        }
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        octal(header, 100, 8, mode);
        octal(header, 108, 8, 0);
        octal(header, 116, 8, 0);
        octal(header, 124, 12, content.length);
        octal(header, 136, 12, 1_700_000_000L);
        header[156] = '0';
        final byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        header[263] = '0';
        header[264] = '0';
        Arrays.fill(header, 148, 156, (byte) ' ');
        int sum = 0;
        for (byte b : header) {
            sum += b & 0xFF;
        }
        final byte[] checksum = String.format("%06o\0 ", sum).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(checksum, 0, header, 148, checksum.length);
        out.write(header);
        out.write(content);
        final int pad = (BLOCK - content.length % BLOCK) % BLOCK;
        if (pad > 0) {
            out.write(new byte[pad]);
        }
        return this;
    }

    /**
     * Finishes the archive.
     *
     * @return the gzip bytes
     * @throws IOException on failure
     */
    public byte[] finish() throws IOException {
        out.write(new byte[BLOCK * 2]);
        out.close();
        return bytes.toByteArray();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private static void octal(final byte[] header, final int offset, final int length, final long value) {
        final byte[] text = String.format("%0" + (length - 1) + "o", value).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, header, offset, text.length);
        header[offset + length - 1] = 0;
    }
}
