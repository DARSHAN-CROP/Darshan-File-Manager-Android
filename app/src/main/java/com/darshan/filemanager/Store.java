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

    static void setLastStatus(Context c, String s) { prefs(c).edit().putString("status", s).apply(); }
    static String lastStatus(Context c) { return prefs(c).getString("status", ""); }

    /** One place that runs a sort and records it (used by buttons and by Auto mode). */
    static Sorter.Result runSort(Context c, int mode, boolean auto) {
        File dir = new File(folder(c));
        Sorter.Result r;
        if (!dir.isDirectory()) {
            r = new Sorter.Result();
            r.errors++;
            r.lines.add("Folder not found: " + dir.getAbsolutePath());
        } else {
            r = Sorter.sort(dir, mode, undoLog(c), Sorter.DEFAULT_MIN_AGE_MS, System.currentTimeMillis());
        }
        String label = (auto ? "Auto " : "") + (mode == Sorter.MODE_EXTENSION ? "Extension sort" : "Type sort");
        List<String> out = new ArrayList<>(r.lines);
        out.add("\u2714 " + label + ": " + r.summary());
        if (!auto || r.moved > 0 || r.errors > 0) log(c, out);
        String status = label + ": " + r.summary();
        if (!auto || r.moved > 0) setLastStatus(c, status);
        return r;
    }
}
