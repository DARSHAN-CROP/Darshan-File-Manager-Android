package com.darshan.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Storage usage: count + size per category (Images, Videos, ...) for a folder and its sub-folders. */
public final class Storage {

    public static final class Usage {
        public final String category;
        public int count;
        public long bytes;
        Usage(String category) { this.category = category; }
    }

    public static final class Report {
        public final List<Usage> rows = new ArrayList<>();   // biggest first, empty categories left out
        public int totalCount;
        public long totalBytes;
    }

    private Storage() {}

    public static Report scan(File dir) {
        Map<String, Usage> map = new LinkedHashMap<>();
        for (String c : Sorter.CATEGORIES) map.put(c, new Usage(c));
        Report rep = new Report();
        walk(dir, map, rep, 0);
        for (Usage u : map.values()) if (u.count > 0) rep.rows.add(u);
        Collections.sort(rep.rows, new Comparator<Usage>() {
            @Override public int compare(Usage a, Usage b) {
                int c = Long.compare(b.bytes, a.bytes);
                return c != 0 ? c : Integer.compare(b.count, a.count);
            }
        });
        return rep;
    }

    private static void walk(File dir, Map<String, Usage> map, Report rep, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) { if (depth < 12) walk(f, map, rep, depth + 1); continue; }
            if (!f.isFile()) continue;
            Usage u = map.get(Sorter.typeFolderFor(f.getName()));
            long len = f.length();
            u.count++;
            u.bytes += len;
            rep.totalCount++;
            rep.totalBytes += len;
        }
    }

    /** 1.5 MB style sizes (1024 based). */
    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, u[i]);
    }
}
