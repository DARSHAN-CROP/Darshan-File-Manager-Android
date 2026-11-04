package com.darshan.filemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Duplicate file finder: groups files by size, then by MD5 of the content. Duplicates are only ever
 * MOVED into a "Duplicates" folder (never deleted) and the move is recorded for Undo.
 */
public final class Duplicates {

    public static final String FOLDER = "Duplicates";

    public static final class Group {
        public final long size;
        public final String hash;
        /** files[0] is the one we keep (oldest; then shortest path); the rest are the copies. */
        public final List<File> files;
        Group(long size, String hash, List<File> files) { this.size = size; this.hash = hash; this.files = files; }
        public long wasted() { return size * (files.size() - 1); }
        public List<File> copies() { return files.subList(1, files.size()); }
    }

    public static final class Scan {
        public final List<Group> groups = new ArrayList<>();
        public int filesScanned;
        public long wasted() { long w = 0; for (Group g : groups) w += g.wasted(); return w; }
        public int copyCount() { int n = 0; for (Group g : groups) n += g.files.size() - 1; return n; }
    }

    private Duplicates() {}

    /** Finds duplicates in dir (and its sub-folders when recursive). Empty files and the Duplicates folder are ignored. */
    public static Scan scan(File dir, boolean recursive, long minAgeMs, long nowMs) {
        Scan s = new Scan();
        List<File> all = new ArrayList<>();
        collect(dir, recursive, all, 0);
        s.filesScanned = all.size();
        Map<Long, List<File>> bySize = new TreeMap<>(Collections.<Long>reverseOrder());
        for (File f : all) {
            long len = f.length();
            if (len <= 0) continue;
            if (minAgeMs > 0 && nowMs - f.lastModified() < minAgeMs) continue;   // still downloading
            List<File> l = bySize.get(len);
            if (l == null) bySize.put(len, l = new ArrayList<>());
            l.add(f);
        }
        for (Map.Entry<Long, List<File>> e : bySize.entrySet()) {
            if (e.getValue().size() < 2) continue;
            Map<String, List<File>> byHash = new LinkedHashMap<>();
            for (File f : e.getValue()) {
                String h = md5(f);
                if (h == null) continue;
                List<File> l = byHash.get(h);
                if (l == null) byHash.put(h, l = new ArrayList<>());
                l.add(f);
            }
            for (Map.Entry<String, List<File>> h : byHash.entrySet()) {
                if (h.getValue().size() < 2) continue;
                List<File> files = new ArrayList<>(h.getValue());
                Collections.sort(files, KEEP_FIRST);
                s.groups.add(new Group(e.getKey(), h.getKey(), files));
            }
        }
        return s;
    }

    /** The original is the oldest file; ties go to the shorter path, then alphabetical. */
    static final Comparator<File> KEEP_FIRST = new Comparator<File>() {
        @Override public int compare(File a, File b) {
            int c = Long.compare(a.lastModified(), b.lastModified());
            if (c != 0) return c;
            c = Integer.compare(a.getAbsolutePath().length(), b.getAbsolutePath().length());
            if (c != 0) return c;
            return a.getAbsolutePath().compareTo(b.getAbsolutePath());
        }
    };

    private static void collect(File dir, boolean recursive, List<File> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (!recursive || depth > 12 || n.startsWith(".") || n.equals(FOLDER) || n.equals("Android")) continue;
                collect(f, true, out, depth + 1);
            } else if (f.isFile() && !Sorter.isSkippedName(n)) {
                out.add(f);
            }
        }
    }

    public static String md5(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b & 255));
            return sb.toString();
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** All copies (everything except each group's first file): what Auto mode moves. */
    public static List<File> allCopies(Scan s) {
        List<File> out = new ArrayList<>();
        for (Group g : s.groups) out.addAll(g.copies());
        return out;
    }

    /** Moves the chosen files into dir/Duplicates (never deletes), appending Undo entries to log. */
    public static void moveToDuplicates(File dir, List<File> chosen, Sorter.Result r, List<String> log) {
        for (File f : chosen) {
            if (!f.isFile()) { r.errors++; r.lines.add("Missing: " + f.getName()); continue; }
            Sorter.moveInto(dir, FOLDER, f, r, log);
        }
    }
}
