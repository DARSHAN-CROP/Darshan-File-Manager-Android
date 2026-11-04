package com.darshan.filemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * One Auto mode run with the actions the user ticked, in a safe order:
 * old files → duplicates → custom rules + type/extension sort → date (Year/Month) sort.
 * All steps share one Undo. Nothing is ever deleted.
 */
public final class AutoRunner {

    public static final class Options {
        public boolean rules = true;
        public boolean sort = true;
        public int sortMode = Sorter.MODE_TYPE;     // MODE_TYPE or MODE_EXTENSION
        public boolean date = false;
        public boolean old = false;
        public int oldDays = 30;
        public boolean duplicates = false;
        public boolean duplicatesInSubfolders = true;
        public List<Rules.Rule> ruleList = new ArrayList<>();

        /** Which steps will run, in order (what the settings screen promises). */
        public List<String> steps() {
            List<String> s = new ArrayList<>();
            if (old && oldDays > 0) s.add("old");
            if (duplicates) s.add("duplicates");
            if (rules && !ruleList.isEmpty()) s.add("rules");
            if (sort) s.add(sortMode == Sorter.MODE_EXTENSION ? "extension" : "type");
            if (date) s.add("date");
            return s;
        }
    }

    public static final class Summary {
        public int sorted, dated, duplicates, old, errors;
        public final List<String> lines = new ArrayList<>();
        public int total() { return sorted + dated + duplicates + old; }
    }

    private AutoRunner() {}

    public static Summary run(File dir, Options o, File undoLog, long minAgeMs, long nowMs) {
        Summary s = new Summary();
        List<String> log = new ArrayList<>();
        if (!dir.isDirectory()) { s.errors++; s.lines.add("Folder not found: " + dir.getAbsolutePath()); return s; }

        if (o.old && o.oldDays > 0) {
            Sorter.Result r = new Sorter.Result();
            OldFiles.move(dir, OldFiles.select(dir, o.oldDays, nowMs), r, log);
            s.old = r.moved; s.errors += r.errors; s.lines.addAll(r.lines);
        }
        if (o.duplicates) {
            Sorter.Result r = new Sorter.Result();
            Duplicates.Scan scan = Duplicates.scan(dir, o.duplicatesInSubfolders, minAgeMs, nowMs);
            Duplicates.moveToDuplicates(dir, Duplicates.allCopies(scan), r, log);
            s.duplicates = r.moved; s.errors += r.errors; s.lines.addAll(r.lines);
        }
        List<Rules.Rule> rules = o.rules ? o.ruleList : null;
        boolean anyRules = rules != null && !rules.isEmpty();
        if (o.sort || anyRules || o.date) {
            int mode = o.sort ? o.sortMode : (o.date ? Sorter.MODE_DATE : Sorter.MODE_RULES_ONLY);
            Sorter.Result r = new Sorter.Result();
            Sorter.sortInto(dir, mode, rules, minAgeMs, nowMs, r, log);
            if (mode == Sorter.MODE_DATE) { s.sorted += r.byRule; s.dated += r.moved - r.byRule; }
            else s.sorted += r.moved;
            s.errors += r.errors; s.lines.addAll(r.lines);
            if (o.date && mode != Sorter.MODE_DATE) {
                Sorter.Result d = new Sorter.Result();
                Sorter.sortInto(dir, Sorter.MODE_DATE, null, minAgeMs, nowMs, d, log);
                s.dated += d.moved; s.errors += d.errors; s.lines.addAll(d.lines);
            }
        }
        Sorter.Result tmp = new Sorter.Result();
        Sorter.saveUndo(undoLog, log, tmp);
        s.lines.addAll(tmp.lines);
        return s;
    }

    /**
     * "12 sorted, 3 duplicates moved, 5 old files moved". fmt = {sorted, dated, duplicates, old, nothing},
     * each with one %d (passed in so the text follows the app language).
     */
    public static String summaryText(Summary s, String[] fmt) {
        StringBuilder sb = new StringBuilder();
        int[] n = {s.sorted, s.dated, s.duplicates, s.old};
        for (int i = 0; i < 4; i++) {
            if (n[i] <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(String.format(java.util.Locale.US, fmt[i], n[i]));
        }
        return sb.length() == 0 ? fmt[4] : sb.toString();
    }
}
