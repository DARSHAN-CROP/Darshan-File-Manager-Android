package com.darshan.filemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings + activity log + undo log, all in the app's private storage. */
final class Store {
    private static final String PREFS = "settings";
    private static final int MAX_LOG = 300;

    private Store() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String defaultFolder() {
        return new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    }

    static String folder(Context c) { return prefs(c).getString("folder", defaultFolder()); }
    static void setFolder(Context c, String f) { prefs(c).edit().putString("folder", f).apply(); }

    static int autoMode(Context c) { return prefs(c).getInt("auto_mode", Sorter.MODE_TYPE); }
    static void setAutoMode(Context c, int m) { prefs(c).edit().putInt("auto_mode", m).apply(); }

    static boolean autoOn(Context c) { return prefs(c).getBoolean("auto_on", false); }
    static void setAutoOn(Context c, boolean on) { prefs(c).edit().putBoolean("auto_on", on).apply(); }

    static File undoLog(Context c) { return new File(c.getFilesDir(), "last_sort_undo.txt"); }
    private static File activityLog(Context c) { return new File(c.getFilesDir(), "activity_log.txt"); }

    /** Only set by the JVM screen tests (the simulator has no real storage permission). */
    static Boolean accessOverrideForTests = null;

    static boolean hasAllFilesAccess(Context c) {
        if (accessOverrideForTests != null) return accessOverrideForTests;
        if (Build.VERSION.SDK_INT >= 30) {
            try { return Environment.isExternalStorageManager(); }
            catch (RuntimeException e) { return false; }      // some OEM builds throw here; never crash
        }
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static synchronized void log(Context c, List<String> lines) {
        if (lines.isEmpty()) return;
        List<String> all = readLog(c);
        String t = new SimpleDateFormat("dd-MM HH:mm", Locale.US).format(new Date());
        for (String l : lines) all.add(t + "   " + l);
        if (all.size() > MAX_LOG) all = new ArrayList<>(all.subList(all.size() - MAX_LOG, all.size()));
        try { Sorter.writeLines(activityLog(c), all); } catch (Exception ignored) {}
    }

    static synchronized void log(Context c, String line) {
        List<String> one = new ArrayList<>();
        one.add(line);
        log(c, one);
    }

    static synchronized List<String> readLog(Context c) {
        try {
            File f = activityLog(c);
            if (f.isFile()) return Sorter.readLines(f);
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    static synchronized void clearLog(Context c) { activityLog(c).delete(); }

    // ---------------------------------------------------------------- v2.0 settings

    static int theme(Context c) { return prefs(c).getInt("theme", Palette.THEME_SYSTEM); }
    static void setTheme(Context c, int t) { prefs(c).edit().putInt("theme", t).apply(); }
    static int accent(Context c) { return Palette.clampAccent(prefs(c).getInt("accent", 0)); }
    static void setAccent(Context c, int a) { prefs(c).edit().putInt("accent", Palette.clampAccent(a)).apply(); }

    /** "en" or "gu". */
    static String lang(Context c) { return "gu".equals(prefs(c).getString("lang", "en")) ? "gu" : "en"; }
    static void setLang(Context c, String l) { prefs(c).edit().putString("lang", "gu".equals(l) ? "gu" : "en").apply(); }

    static boolean isNight(Context c) {
        int night = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return Palette.isDark(theme(c), night == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }
    static Palette palette(Context c) { return new Palette(isNight(c), accent(c)); }

    static List<Rules.Rule> rules(Context c) { return Rules.decode(prefs(c).getString("rules", "")); }
    static void setRules(Context c, List<Rules.Rule> r) { prefs(c).edit().putString("rules", Rules.encode(r)).apply(); }

    static int oldDays(Context c) { return Math.max(1, prefs(c).getInt("old_days", 30)); }
    static void setOldDays(Context c, int d) { prefs(c).edit().putInt("old_days", Math.max(1, d)).apply(); }
    static boolean dupSubfolders(Context c) { return prefs(c).getBoolean("dup_sub", true); }
    static void setDupSubfolders(Context c, boolean b) { prefs(c).edit().putBoolean("dup_sub", b).apply(); }
    static boolean notifyManual(Context c) { return prefs(c).getBoolean("notify_manual", false); }
    static void setNotifyManual(Context c, boolean b) { prefs(c).edit().putBoolean("notify_manual", b).apply(); }
    static int tab(Context c) { return prefs(c).getInt("tab", 0); }
    static void setTab(Context c, int t) { prefs(c).edit().putInt("tab", t).apply(); }

    /** Auto mode ticks. Default: custom rules + type sort on, the rest off. */
    static final String AUTO_RULES = "auto_rules", AUTO_SORT = "auto_sort", AUTO_DATE = "auto_date",
            AUTO_OLD = "auto_old", AUTO_DUPS = "auto_dups";
    static boolean autoTick(Context c, String key) {
        boolean def = AUTO_RULES.equals(key) || AUTO_SORT.equals(key);
        return prefs(c).getBoolean(key, def);
    }
    static void setAutoTick(Context c, String key, boolean on) { prefs(c).edit().putBoolean(key, on).apply(); }

    static AutoRunner.Options autoOptions(Context c) {
        AutoRunner.Options o = new AutoRunner.Options();
        o.rules = autoTick(c, AUTO_RULES);
        o.sort = autoTick(c, AUTO_SORT);
        o.sortMode = autoMode(c) == Sorter.MODE_EXTENSION ? Sorter.MODE_EXTENSION : Sorter.MODE_TYPE;
        o.date = autoTick(c, AUTO_DATE);
        o.old = autoTick(c, AUTO_OLD);
        o.oldDays = oldDays(c);
        o.duplicates = autoTick(c, AUTO_DUPS);
        o.duplicatesInSubfolders = dupSubfolders(c);
        o.ruleList = rules(c);
        return o;
    }

    /** Text in the app's language (Auto mode runs without an Activity). */
    static Context localized(Context c) { return Lang.wrap(c); }

    static String movedText(Context c, int moved, int errors) {
        Context l = localized(c);
        String s = moved == 1 ? l.getString(R.string.files_moved_one) : l.getString(R.string.files_moved, moved);
        if (errors > 0) s += l.getString(R.string.errors_n, errors);
        return s;
    }

    static String[] summaryFormats(Context c) {
        Context l = localized(c);
        return new String[]{l.getString(R.string.sum_sorted), l.getString(R.string.sum_dated),
                l.getString(R.string.sum_dups), l.getString(R.string.sum_old), l.getString(R.string.sum_nothing)};
    }

    /** One Auto mode run with the ticked actions; logs, updates status, notifies. */
    static AutoRunner.Summary runAuto(Context c) {
        AutoRunner.Summary s = AutoRunner.run(new File(folder(c)), autoOptions(c), undoLog(c),
                Sorter.DEFAULT_MIN_AGE_MS, System.currentTimeMillis());
        String text = AutoRunner.summaryText(s, summaryFormats(c));
        if (s.errors > 0) text += localized(c).getString(R.string.errors_n, s.errors);
        String line = localized(c).getString(R.string.auto_run, text);
        if (s.total() > 0 || s.errors > 0) {
            List<String> out = new ArrayList<>(s.lines);
            out.add("\u2714 " + line);
            log(c, out);
            setLastStatus(c, line);
        }
        if (s.total() > 0) Notifier.show(c, true, text);
        return s;
    }

    static void setLastStatus(Context c, String s) { prefs(c).edit().putString("status", s).apply(); }
    static String lastStatus(Context c) { return prefs(c).getString("status", ""); }

    /** One place that runs a sort and records it (used by buttons). Custom rules are applied first. */
    static Sorter.Result runSort(Context c, int mode, boolean auto) {
        File dir = new File(folder(c));
        Sorter.Result r;
        if (!dir.isDirectory()) {
            r = new Sorter.Result();
            r.errors++;
            r.lines.add("Folder not found: " + dir.getAbsolutePath());
        } else {
            r = Sorter.sort(dir, mode, rules(c), undoLog(c), Sorter.DEFAULT_MIN_AGE_MS, System.currentTimeMillis());
        }
        String label = (auto ? localized(c).getString(R.string.auto_prefix) + " " : "") + sortLabel(c, mode);
        String status = label + ": " + movedText(c, r.moved, r.errors);
        List<String> out = new ArrayList<>(r.lines);
        out.add("\u2714 " + status);
        if (!auto || r.moved > 0 || r.errors > 0) log(c, out);
        if (!auto || r.moved > 0) setLastStatus(c, status);
        if (!auto && r.moved > 0 && notifyManual(c)) Notifier.show(c, false, status);
        return r;
    }

    static String sortLabel(Context c, int mode) {
        Context l = localized(c);
        if (mode == Sorter.MODE_EXTENSION) return l.getString(R.string.ext_sort);
        if (mode == Sorter.MODE_DATE) return l.getString(R.string.date_sort);
        return l.getString(R.string.type_sort);
    }

    /** Records a Tools action (duplicates, old files, rename) so Undo can reverse it. */
    static void finishAction(Context c, List<String> undoEntries, Sorter.Result r, String status) {
        Sorter.saveUndo(undoLog(c), undoEntries, r);
        List<String> out = new ArrayList<>(r.lines);
        out.add("\u2714 " + status);
        log(c, out);
        setLastStatus(c, status);
    }
}
