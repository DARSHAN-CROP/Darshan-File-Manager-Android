package com.darshan.filemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom rules: "extension(s) → folder" or "name contains X → folder". Checked before the built-in type map,
 * first matching rule wins. Stored as text lines (kind TAB pattern TAB folder), no JSON library needed.
 */
public final class Rules {

    public static final int KIND_EXT = 0, KIND_NAME = 1;

    public static final class Rule {
        public final int kind;
        public final String pattern;
        public final String folder;
        public Rule(int kind, String pattern, String folder) {
            this.kind = kind == KIND_NAME ? KIND_NAME : KIND_EXT;
            this.pattern = pattern == null ? "" : pattern.trim();
            this.folder = cleanFolder(folder);
        }
        public boolean isValid() { return !pattern.isEmpty() && !folder.isEmpty(); }

        public boolean matches(String fileName) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (kind == KIND_NAME) return !pattern.isEmpty() && lower.contains(pattern.toLowerCase(Locale.ROOT));
            for (String e : extensions(pattern)) {
                if (lower.endsWith("." + e) && lower.length() > e.length() + 1) return true;
            }
            return false;
        }
    }

    private Rules() {}

    /** "jpg, .PNG  heic" → [jpg, png, heic] */
    public static List<String> extensions(String pattern) {
        List<String> out = new ArrayList<>();
        for (String p : pattern.split("[,;\\s]+")) {
            String e = p.trim().toLowerCase(Locale.ROOT);
            while (e.startsWith(".")) e = e.substring(1);
            if (!e.isEmpty()) out.add(e);
        }
        return out;
    }

    /** Folder names stay inside the chosen folder: no slashes, no "..", no characters Android rejects. */
    public static String cleanFolder(String f) {
        if (f == null) return "";
        String s = f.trim().replaceAll("[\\\\/:*?\"<>|\\t\\n]", "_");
        while (s.startsWith(".")) s = s.substring(1);
        return s.trim();
    }

    /** First matching rule's folder, or null when no rule matches. */
    public static String folderFor(String fileName, List<Rule> rules) {
        if (rules == null) return null;
        for (Rule r : rules) if (r.isValid() && r.matches(fileName)) return r.folder;
        return null;
    }

    public static String encode(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        for (Rule r : rules) {
            if (!r.isValid()) continue;
            sb.append(r.kind).append('\t').append(r.pattern.replace('\t', ' ').replace('\n', ' '))
              .append('\t').append(r.folder).append('\n');
        }
        return sb.toString();
    }

    public static List<Rule> decode(String s) {
        List<Rule> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            String[] p = line.split("\t");
            if (p.length != 3) continue;
            int kind;
            try { kind = Integer.parseInt(p[0].trim()); } catch (NumberFormatException e) { continue; }
            Rule r = new Rule(kind, p[1], p[2]);
            if (r.isValid()) out.add(r);
        }
        return out;
    }
}
