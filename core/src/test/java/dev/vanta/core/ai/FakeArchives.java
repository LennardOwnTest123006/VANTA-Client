package dev.vanta.core.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds small zip and tar.gz archives in memory for the extractor and installer tests. The tar writer produces
 * plain ustar headers plus, on request, GNU long-name ({@code L}) and pax ({@code x}) entries.
 */
public final class FakeArchives {
    /** One tar entry. */
    record TarEntry(String name, byte[] data, int mode, char type, String linkName) {
        static TarEntry file(String name, byte[] data, int mode) {
            return new TarEntry(name, data, mode, '0', "");
        }

        static TarEntry file(String name, String text, int mode) {
            return file(name, text.getBytes(StandardCharsets.UTF_8), mode);
        }

        static TarEntry dir(String name) {
            return new TarEntry(name.endsWith("/") ? name : name + "/", new byte[0], 0755, '5', "");
        }

        static TarEntry symlink(String name, String target) {
            return new TarEntry(name, new byte[0], 0777, '2', target);
        }
    }

    private FakeArchives() {
    }

    /** Deterministic pseudo-random bytes. */
    public static byte[] randomBytes(int size, long seed) {
        byte[] data = new byte[size];
        new Random(seed).nextBytes(data);
        return data;
    }

    /** A zip with the given name -> content pairs. Names ending in "/" become directories. */
    static byte[] zip(List<String> names, List<byte[]> contents) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (int i = 0; i < names.size(); i++) {
                    zip.putNextEntry(new ZipEntry(names.get(i)));
                    if (!names.get(i).endsWith("/")) {
                        zip.write(contents.get(i));
                    }
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The fake Windows runtime archive: llama-server.exe plus a dll and a readme at the root. */
    public static byte[] windowsRuntimeZip() {
        return zip(List.of("llama-server.exe", "ggml.dll", "README.md"),
                List.of("MZ fake llama-server".getBytes(StandardCharsets.UTF_8),
                        randomBytes(3000, 7), "fake readme".getBytes(StandardCharsets.UTF_8)));
    }

    /** The fake Linux runtime archive: build/bin/llama-server (0755) and a shared library. */
    public static byte[] linuxRuntimeTarGz() {
        return tarGz(List.of(TarEntry.dir("build"), TarEntry.dir("build/bin"),
                TarEntry.file("build/bin/llama-server", "#!/bin/sh\necho fake llama-server\n", 0755),
                TarEntry.file("build/bin/libggml.so", randomBytes(2048, 11), 0644),
                TarEntry.file("build/bin/LICENSE", "MIT", 0644)));
    }

    /** Gzip-compressed tar of the entries. */
    static byte[] tarGz(List<TarEntry> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(tar(entries));
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Uncompressed ustar archive of the entries; names longer than 100 bytes get a GNU long-name entry. */
    static byte[] tar(List<TarEntry> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TarEntry entry : entries) {
            byte[] nameBytes = entry.name().getBytes(StandardCharsets.UTF_8);
            if (nameBytes.length > 100) {
                byte[] longName = (entry.name() + "\0").getBytes(StandardCharsets.UTF_8);
                writeEntry(out, "././@LongLink", longName, 0644, 'L', "");
                writeEntry(out, entry.name().substring(0, 100), entry.data(), entry.mode(), entry.type(),
                        entry.linkName());
            } else {
                writeEntry(out, entry.name(), entry.data(), entry.mode(), entry.type(), entry.linkName());
            }
        }
        out.writeBytes(new byte[1024]);
        return out.toByteArray();
    }

    /** A tar whose first entry is a pax header giving the real path of the following file. */
    static byte[] tarWithPaxPath(String shortName, String realPath, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String record = "path=" + realPath + "\n";
        int length = record.length() + 3;
        if (String.valueOf(length).length() + 1 + record.length() != length) {
            length = String.valueOf(length).length() + 1 + record.length();
        }
        byte[] pax = (length + " " + record).getBytes(StandardCharsets.UTF_8);
        writeEntry(out, "PaxHeader/" + shortName, pax, 0644, 'x', "");
        writeEntry(out, shortName, data, 0644, '0', "");
        out.writeBytes(new byte[1024]);
        return out.toByteArray();
    }

    static List<TarEntry> list(TarEntry... entries) {
        return new ArrayList<>(List.of(entries));
    }

    private static void writeEntry(ByteArrayOutputStream out, String name, byte[] data, int mode, char type,
                                   String linkName) {
        byte[] header = new byte[512];
        put(header, 0, 100, name);
        put(header, 100, 8, String.format("%07o", mode));
        put(header, 108, 8, "0000000");
        put(header, 116, 8, "0000000");
        put(header, 124, 12, String.format("%011o", data.length));
        put(header, 136, 12, String.format("%011o", 1_700_000_000L));
        header[156] = (byte) type;
        put(header, 157, 100, linkName);
        put(header, 257, 6, "ustar");
        header[263] = '0';
        header[264] = '0';
        put(header, 265, 32, "vanta");
        put(header, 297, 32, "vanta");
        put(header, 329, 8, "0000000");
        put(header, 337, 8, "0000000");
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        int sum = 0;
        for (byte b : header) {
            sum += b & 0xFF;
        }
        put(header, 148, 7, String.format("%06o", sum));
        header[155] = ' ';
        out.writeBytes(header);
        out.writeBytes(data);
        int padding = (512 - data.length % 512) % 512;
        out.writeBytes(new byte[padding]);
    }

    private static void put(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }
}
