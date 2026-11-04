package com.darshan.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Batch rename. Filter (name contains / extensions), then: find → replace, numbering (Photo_001),
 * prefix + suffix. Always previewed first; applying records every rename so Undo puts the old names back.
 */
public final class Renamer {

    public static final class Spec {
        public String contains = "", extensions = "";
        public String find = "", replace = "";
        public String prefix = "", suffix = "";
        public boolean numbering;
        public String base = "";          // e.g. "Photo" → Photo_001.jpg (empty: number goes after the old name)
        public int start = 1, digits = 3;
    }

    public static final class Item {
        public final File from;
        public final String newName;
        public final boolean conflict;    // the name is taken: it will get " (1)" added, never overwrite
        Item(File from, String newName, boolean conflict) { this.from = from; this.newName = newName; this.conflict = conflict; }
        public boolean changes() { return !from.getName().equals(newName); }
    }

    private Renamer() {}

    public static boolean matches(String name, Spec s) {
        String l = name.toLowerCase(Locale.ROOT);
        if (s.contains != null && !s.contains.trim().isEmpty() && !l.contains(s.contains.trim().toLowerCase(Locale.ROOT)))
            return false;
        List<String> exts = Rules.extensions(s.extensions == null ? "" : s.extensions);
        if (exts.isEmpty()) return true;
        for (String e : exts) if (l.endsWith("." + e)) return true;
        return false;
    }

    /** New name for the index-th matching file (0 based). */
    public static String newName(String name, int index, Spec s) {
        String ext = Sorter.extensionOf(name);
        String stem = ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
        String dotExt = ext == null ? "" : "." + ext;
        if (s.find != null && !s.find.isEmpty()) {
            stem = Pattern.compile(Pattern.quote(s.find), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(stem).replaceAll(Matcher.quoteReplacement(s.replace == null ? "" : s.replace));
        }
        if (s.numbering) {
            String num = String.format(Locale.US, "%0" + Math.max(1, Math.min(s.digits, 9)) + "d", s.start + index);
            String b = s.base == null ? "" : s.base.trim();
            stem = (b.isEmpty() ? stem : b) + "_" + num;
        }
        stem = (s.prefix == null ? "" : s.prefix) + stem + (s.suffix == null ? "" : s.suffix);
        String out = (stem + dotExt).replaceAll("[\\\\/:*?\"<>|\\t\\n]", "_").trim();
        if (out.isEmpty() || out.startsWith(".") && !name.startsWith(".")) return name;
        return out;
    }

    /** Preview: every matching file in dir (A→Z) with its new name. */
    public static List<Item> plan(File dir, Spec s) {
        List<Item> out = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        Set<String> taken = new HashSet<>();
        List<File> matching = new ArrayList<>();
        for (File f : files) {
            if (!f.isFile() || Sorter.isSkippedName(f.getName())) continue;
            if (matches(f.getName(), s)) matching.add(f);
        }
        Set<String> leaving = new HashSet<>();
        for (File f : files) taken.add(f.getName().toLowerCase(Locale.ROOT));
        int i = 0;
        Set<String> planned = new HashSet<>();
        for (File f : matching) {
            String n = newName(f.getName(), i++, s);
            String key = n.toLowerCase(Locale.ROOT);
            boolean self = key.equals(f.getName().toLowerCase(Locale.ROOT));
            boolean conflict = !self && (taken.contains(key) || planned.contains(key));
            planned.add(key);
            out.add(new Item(f, n, conflict));
        }
        return out;
    }

    /** Renames, never overwriting; appends Undo entries (M new old) to log. */
    public static void apply(List<Item> items, Sorter.Result r, List<String> log) {
        for (Item it : items) {
            if (!it.changes()) { r.skipped++; continue; }
            File src = it.from;
            if (!src.isFile()) { r.errors++; r.lines.add("Missing: " + src.getName()); continue; }
            File dir = src.getParentFile();
            File dst;
            boolean ok;
            if (it.newName.equalsIgnoreCase(src.getName())) {
                // only the letter case changes: go through a temporary name (phone storage ignores case)
                File tmp = Sorter.uniqueDest(dir, ".rename-" + System.nanoTime());
                dst = new File(dir, it.newName);
                ok = tmp != null && src.renameTo(tmp) && (tmp.renameTo(dst) || !tmp.renameTo(src));
            } else {
                dst = Sorter.uniqueDest(dir, it.newName);
                ok = dst != null && Sorter.moveFile(src, dst);
            }
            if (!ok) { r.errors++; r.lines.add("Error renaming " + src.getName()); continue; }
            r.moved++;
            log.add("M\t" + dst.getAbsolutePath() + "\t" + src.getAbsolutePath());
            r.lines.add(src.getName() + "  \u2192  " + dst.getName());
        }
    }
}
