package com.darshan.filemanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Small helpers to make files with content and an age. */
final class TestFiles {
    static final long HOUR = 3_600_000L;
    private TestFiles() {}

    static File tmpDir() throws IOException {
        File d = Files.createTempDirectory("dfm").toFile();
        d.deleteOnExit();
        return d;
    }

    static File make(File dir, String name, String content, long ageMs) throws IOException {
        File f = new File(dir, name);
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(content.getBytes(StandardCharsets.UTF_8)); }
        f.setLastModified(System.currentTimeMillis() - ageMs);
        return f;
    }

    static File make(File dir, String name, String content) throws IOException {
        return make(dir, name, content, HOUR);
    }
}
