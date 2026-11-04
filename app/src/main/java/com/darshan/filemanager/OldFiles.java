package com.darshan.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Old files cleaner: files (directly in the folder) not modified for N days move to "Old Files". Undo-able. */
public final class OldFiles {

    public static final String FOLDER = "Old Files";
    public static final int[] PRESET_DAYS = {30, 60, 90};
    public static final long DAY_MS = 24L * 60 * 60 * 1000;

    private OldFiles() {}

    public static List<File> select(File dir, int days, long nowMs) {
        List<File> out = new ArrayList<>();
        if (days <= 0) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        long cutoff = nowMs - days * DAY_MS;
        for (File f : files) {
            if (!f.isFile() || Sorter.isSkippedName(f.getName())) continue;
            if (f.lastModified() < cutoff) out.add(f);
        }
        return out;
    }

    public static long totalSize(List<File> files) {
        long t = 0;
        for (File f : files) t += f.length();
        return t;
    }

    public static void move(File dir, List<File> files, Sorter.Result r, List<String> log) {
        for (File f : files) {
            if (!f.isFile()) { r.errors++; r.lines.add("Missing: " + f.getName()); continue; }
            Sorter.moveInto(dir, FOLDER, f, r, log);
        }
    }
}
