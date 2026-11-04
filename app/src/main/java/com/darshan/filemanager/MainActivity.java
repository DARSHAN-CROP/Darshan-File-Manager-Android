package com.darshan.filemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 7, REQ_NOTIF = 8;
    static final int TAB_SORT = 0, TAB_TOOLS = 1, TAB_STATS = 2, TAB_SETTINGS = 3;
    private static final String[] TAB_ICONS = {"\uD83E\uDDF9", "\uD83E\uDDF0", "\uD83D\uDCCA", "\u2699\uFE0F"};
    private static final int[] TAB_LABELS = {R.string.tab_sort, R.string.tab_tools, R.string.tab_stats, R.string.tab_settings};
    private static final int[] COLOR_NAMES = {R.string.color_maroon, R.string.color_gold, R.string.color_blue,
            R.string.color_green, R.string.color_purple, R.string.color_orange};

    private Palette pal;
    private View permCard;
    private TextView folderPath, status, logText;
    private Button btnDate, btnExt, btnType, btnDateSort, btnUndo;
    private Switch swAuto;
    private RadioButton rbType, rbExt;
    private CheckBox cbAutoRules, cbAutoSort, cbAutoDate, cbAutoOld, cbAutoDups;
    private View[] pages;
    private final List<View[]> tabViews = new ArrayList<>();
    private int currentTab;
    private boolean busy;

    // Tools state
    private final Map<CheckBox, File> dupChecks = new LinkedHashMap<>();
    private List<File> oldPreview;
    private List<Renamer.Item> renPlan;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Store.isNight(this) ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permCard = findViewById(R.id.permCard);
        folderPath = (TextView) findViewById(R.id.folderPath);
        status = (TextView) findViewById(R.id.status);
        logText = (TextView) findViewById(R.id.logText);
        btnDate = (Button) findViewById(R.id.btnDate);
        btnExt = (Button) findViewById(R.id.btnExt);
        btnType = (Button) findViewById(R.id.btnType);
        btnDateSort = (Button) findViewById(R.id.btnDateSort);
        btnUndo = (Button) findViewById(R.id.btnUndo);
        swAuto = (Switch) findViewById(R.id.swAuto);
        rbType = (RadioButton) findViewById(R.id.rbType);
        rbExt = (RadioButton) findViewById(R.id.rbExt);
        pages = new View[]{findViewById(R.id.pageSort), findViewById(R.id.pageTools),
                findViewById(R.id.pageStats), findViewById(R.id.pageSettings)};

        setupSortPage();
        setupToolsPage();
        setupStatsPage();
        setupSettingsPage();
        buildTabBar();
        currentTab = Store.tab(this);                     // kept across theme/language switches
        applyTheme();
    }

    // ================================================================= theme + tabs

    private void applyTheme() {
        pal = Store.palette(this);
        float d = getResources().getDisplayMetrics().density;
        getWindow().getDecorView().setBackgroundColor(pal.bg);
        findViewById(R.id.root).setBackgroundColor(pal.bg);
        findViewById(R.id.header).setBackgroundColor(pal.accent);
        ((TextView) findViewById(R.id.headerTitle)).setTextColor(pal.onAccent);
        ((TextView) findViewById(R.id.headerSub)).setTextColor(Palette.withAlpha(pal.onAccent, 0xCC));
        TextView ver = (TextView) findViewById(R.id.headerVersion);
        ver.setText("v" + versionName());
        ver.setTextColor(pal.onAccent);
        ver.setBackground(UiTheme.round(Palette.withAlpha(pal.onAccent, 0x22), Palette.withAlpha(pal.onAccent, 0x66), 10 * d, 1 * d));
        findViewById(R.id.stripe).setBackgroundColor(pal.highlight);
        findViewById(R.id.tabDivider).setBackgroundColor(pal.stroke);
        findViewById(R.id.tabBar).setBackgroundColor(pal.card);
        getWindow().setStatusBarColor(pal.accent);
        getWindow().setNavigationBarColor(pal.card);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) {
            if (pal.onAccent == Palette.INK) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 27) {
            if (!pal.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
        for (View p : pages) UiTheme.apply(p, pal);
        renderSwatches();
        ((BarChartView) findViewById(R.id.chart)).setPalette(pal);
        selectTab(currentTab);
    }

    private void buildTabBar() {
        LinearLayout bar = (LinearLayout) findViewById(R.id.tabBar);
        bar.removeAllViews();
        tabViews.clear();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            View ind = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(36 * d), Math.round(3 * d));
            ind.setLayoutParams(lp);
            TextView icon = new TextView(this);
            icon.setText(TAB_ICONS[i]);
            icon.setTextSize(19);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.topMargin = Math.round(6 * d);
            icon.setLayoutParams(ilp);
            TextView label = new TextView(this);
            label.setText(TAB_LABELS[i]);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            t.addView(ind);
            t.addView(icon);
            t.addView(label);
            t.setId(View.generateViewId());
            t.setContentDescription(getString(TAB_LABELS[i]));
            t.setOnClickListener(v -> selectTab(idx));
            bar.addView(t);
            tabViews.add(new View[]{t, ind, icon, label});
        }
    }

    /** Switches the visible section (Sort / Tools / Stats / Settings). */
    void selectTab(int idx) {
        if (idx < 0 || idx > 3) idx = 0;
        currentTab = idx;
        Store.setTab(this, idx);
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        if (pal == null) return;
        for (int i = 0; i < tabViews.size(); i++) {
            View[] tv = tabViews.get(i);
            boolean on = i == idx;
            tv[1].setBackgroundColor(on ? pal.accent : 0);
            TextView label = (TextView) tv[3];
            label.setTextColor(on ? pal.accentText : pal.textSoft);
            label.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv[2].setAlpha(on ? 1f : 0.6f);
        }
        if (idx == TAB_STATS) loadStats();
        if (idx == TAB_SETTINGS) refreshNotifState();
    }

    View tabView(int idx) { return tabViews.get(idx)[0]; }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "2.0"; }
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, pal != null && pal.dark
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private AlertDialog show(AlertDialog.Builder b) {
        AlertDialog d = b.show();
        for (int which : new int[]{DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL}) {
            Button btn = d.getButton(which);
            if (btn != null) btn.setTextColor(Palette.readableOn(pal.accent, pal.dark ? 0xFF2D2D33 : Palette.WHITE, 4.5));
        }
        return d;
    }

    // ================================================================= SORT page

    private void setupSortPage() {
        findViewById(R.id.btnPerm).setOnClickListener(v -> askPermission());
        findViewById(R.id.btnChoose).setOnClickListener(v -> {
            if (!checkAccess()) return;
            File start = new File(Store.folder(MainActivity.this));
            if (!start.isDirectory()) start = Environment.getExternalStorageDirectory();
            showFolderPicker(start);
        });
        findViewById(R.id.btnDownloads).setOnClickListener(v -> {
            Store.setFolder(MainActivity.this, Store.defaultFolder());
            refresh();
        });
        btnDate.setOnClickListener(v -> doDateFolder());
        btnExt.setOnClickListener(v -> doSort(Sorter.MODE_EXTENSION));
        btnType.setOnClickListener(v -> doSort(Sorter.MODE_TYPE));
        btnDateSort.setOnClickListener(v -> doSort(Sorter.MODE_DATE));
        btnUndo.setOnClickListener(v -> confirmUndo());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> { Store.clearLog(MainActivity.this); refresh(); });

        if (Store.autoMode(this) == Sorter.MODE_EXTENSION) rbExt.setChecked(true); else rbType.setChecked(true);
        ((RadioGroup) findViewById(R.id.rgMode)).setOnCheckedChangeListener((g, id) ->
                Store.setAutoMode(MainActivity.this, id == R.id.rbExt ? Sorter.MODE_EXTENSION : Sorter.MODE_TYPE));
        swAuto.setOnCheckedChangeListener((b, on) -> setAuto(on));

        cbAutoRules = tick(R.id.cbAutoRules, Store.AUTO_RULES);
        cbAutoSort = tick(R.id.cbAutoSort, Store.AUTO_SORT);
        cbAutoDate = tick(R.id.cbAutoDate, Store.AUTO_DATE);
        cbAutoOld = tick(R.id.cbAutoOld, Store.AUTO_OLD);
        cbAutoDups = tick(R.id.cbAutoDups, Store.AUTO_DUPS);
        cbAutoSort.setOnCheckedChangeListener((b, on) -> {
            Store.setAutoTick(MainActivity.this, Store.AUTO_SORT, on);
            updateAutoModeRadios();
        });
        updateAutoModeRadios();
    }

    private CheckBox tick(int id, final String key) {
        CheckBox cb = (CheckBox) findViewById(id);
        cb.setChecked(Store.autoTick(this, key));
        cb.setOnCheckedChangeListener((b, on) -> Store.setAutoTick(MainActivity.this, key, on));
        return cb;
    }

    private void updateAutoModeRadios() {
        boolean on = cbAutoSort.isChecked();
        rbType.setEnabled(on);
        rbExt.setEnabled(on);
        rbType.setAlpha(on ? 1f : 0.45f);
        rbExt.setAlpha(on ? 1f : 0.45f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // keep the switch honest with what the system actually has scheduled
        boolean on = Store.autoOn(this);
        if (on && !AutoSortJob.isScheduled(this)) AutoSortJob.schedule(this);
        swAuto.setOnCheckedChangeListener(null);
        swAuto.setChecked(on);
        swAuto.setOnCheckedChangeListener((b, v) -> setAuto(v));
        refresh();
    }

    private void refresh() {
        boolean ok = Store.hasAllFilesAccess(this);
        permCard.setVisibility(ok ? View.GONE : View.VISIBLE);
        folderPath.setText(prettyPath(Store.folder(this)));
        btnUndo.setEnabled(!busy && Sorter.hasUndo(Store.undoLog(this)));
        for (int id : new int[]{R.id.btnDate, R.id.btnExt, R.id.btnType, R.id.btnDateSort, R.id.btnDupScan,
                R.id.btnDupMove, R.id.btnOldPreview, R.id.btnOldMove, R.id.btnRenPreview, R.id.btnRenApply, R.id.btnStatsRefresh})
            findViewById(id).setEnabled(!busy);
        String st = Store.lastStatus(this);
        if (!busy) status.setText(!ok ? getString(R.string.allow_to_start) : (st.isEmpty() ? getString(R.string.ready) : st));
        List<String> log = Store.readLog(this);
        if (log.isEmpty()) logText.setText(R.string.no_activity);
        else {
            Collections.reverse(log);                     // newest first
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < log.size() && i < 150; i++) { if (i > 0) sb.append('\n'); sb.append(log.get(i)); }
            logText.setText(sb.toString());
        }
        cbAutoOld.setText(getString(R.string.auto_old, Store.oldDays(this)));
        ((TextView) findViewById(R.id.statsFolder)).setText(prettyPath(Store.folder(this)));
        ((TextView) findViewById(R.id.aboutText)).setText(getString(R.string.about_text, versionName()));
    }

    private String prettyPath(String p) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String is = getString(R.string.internal_storage);
        if (p.equals(root)) return is;
        if (p.startsWith(root + "/")) return is + "/" + p.substring(root.length() + 1);
        return p;
    }

    // ---------------------------------------------------------------- permission

    private boolean checkAccess() {
        if (Store.hasAllFilesAccess(this)) return true;
        show(dialog()
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_dialog_text)
                .setPositiveButton(R.string.allow, (d, w) -> askPermission())
                .setNegativeButton(R.string.cancel, null));
        return false;
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (ActivityNotFoundException e2) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                }
            }
            Toast.makeText(this, R.string.perm_toast, Toast.LENGTH_LONG).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        } else if (!Notifier.allowed(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } catch (ActivityNotFoundException ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_STORAGE) {
            boolean granted = results.length > 0;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // "Don't ask again" was chosen: send them to the app's settings page
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
            refresh();
        }
        if (code == REQ_NOTIF) refreshNotifState();
    }

    // ---------------------------------------------------------------- background work

    private interface Work { String run(Context app); }

    /** Runs file work off the UI thread; the returned text becomes the status line. */
    private void runInBackground(final String working, final Work work) {
        busy = true;
        status.setText(working);
        refresh();
        final Context app = getApplicationContext();
        new Thread(() -> {
            String msg;
            try { msg = work.run(app); }
            catch (Throwable t) { msg = "Error: " + t; Store.log(app, msg); }
            final String fm = msg;
            runOnUiThread(() -> {
                busy = false;
                refresh();
                if (fm != null) status.setText(fm);
            });
        }, "work").start();
    }

    // ---------------------------------------------------------------- sort actions

    private void doDateFolder() {
        if (!checkAccess()) return;
        File dir = new File(Store.folder(this));
        boolean[] created = new boolean[1];
        File f = dir.isDirectory() ? Sorter.makeDateFolder(dir, new Date(), created) : null;
        String msg;
        if (f == null) msg = getString(R.string.folder_failed);
        else msg = getString(created[0] ? R.string.folder_created : R.string.folder_exists, f.getName());
        Store.log(this, msg);
        Store.setLastStatus(this, msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void doSort(final int mode) {
        if (!checkAccess() || busy) return;
        File dir = new File(Store.folder(this));
        if (isRiskyFolder(dir)) {
            show(dialog()
                    .setTitle(R.string.sort_risky_title)
                    .setMessage(getString(R.string.sort_risky_text, prettyPath(dir.getAbsolutePath())))
                    .setPositiveButton(R.string.sort, (d, w) -> startSort(mode))
                    .setNegativeButton(R.string.cancel, null));
        } else {
            startSort(mode);
        }
    }

    private static boolean isRiskyFolder(File dir) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String p = dir.getAbsolutePath();
        return p.equals(root) || p.equals(root + "/Android") || p.startsWith(root + "/Android/") || p.equals("/");
    }

    private void startSort(final int mode) {
        runInBackground(getString(R.string.sorting), app -> {
            Sorter.Result r = Store.runSort(app, mode, false);
            if (r.moved == 0 && r.errors == 0) runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, R.string.nothing_to_move, Toast.LENGTH_SHORT).show());
            return Store.sortLabel(app, mode) + ": " + Store.movedText(app, r.moved, r.errors);
        });
    }

    private void confirmUndo() {
        if (!checkAccess() || busy) return;
        show(dialog()
                .setTitle(R.string.undo_title)
                .setMessage(R.string.undo_text)
                .setPositiveButton(R.string.undo, (d, w) -> runUndo())
                .setNegativeButton(R.string.cancel, null));
    }

    private void runUndo() {
        runInBackground(getString(R.string.undoing), app -> {
            Sorter.Result r = Sorter.undo(Store.undoLog(app));
            List<String> out = new ArrayList<>(r.lines);
            Context l = Store.localized(app);
            String s = l.getString(R.string.undo_done, r.moved) + (r.errors > 0 ? l.getString(R.string.undo_failed_n, r.errors) : "");
            out.add(s);
            Store.log(app, out);
            Store.setLastStatus(app, s);
            return s;
        });
    }

    private void setAuto(boolean on) {
        if (on && !Store.hasAllFilesAccess(this)) {
            swAuto.setChecked(false);
            checkAccess();
            return;
        }
        if (!on && !Store.autoOn(this)) { refresh(); return; }
        Store.setAutoOn(this, on);
        if (on) {
            boolean ok = AutoSortJob.schedule(this);
            String m = getString(ok ? R.string.auto_on : R.string.auto_failed);
            Store.log(this, m);
            Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            if (ok && !Notifier.allowed(this) && Build.VERSION.SDK_INT >= 33) askNotificationPermission();
        } else {
            AutoSortJob.cancel(this);
            Store.log(this, getString(R.string.auto_off));
        }
        refresh();
    }

    // ---------------------------------------------------------------- folder picker

    private void showFolderPicker(final File dir) {
        File[] subs = dir.listFiles();
        final List<File> dirs = new ArrayList<>();
        if (subs != null) {
            Arrays.sort(subs);
            for (File f : subs) if (f.isDirectory() && !f.getName().startsWith(".")) dirs.add(f);
        }
        final File root = Environment.getExternalStorageDirectory();
        final boolean canGoUp = !dir.getAbsolutePath().equals(root.getAbsolutePath()) && dir.getParentFile() != null;
        List<String> names = new ArrayList<>();
        if (canGoUp) names.add(getString(R.string.up));
        for (File f : dirs) names.add("\uD83D\uDCC1  " + f.getName());
        if (dirs.isEmpty()) names.add(getString(R.string.no_subfolders));

        show(dialog()
                .setTitle(prettyPath(dir.getAbsolutePath()))
                .setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names), (d, which) -> {
                    int idx = canGoUp ? which - 1 : which;
                    if (canGoUp && which == 0) showFolderPicker(dir.getParentFile());
                    else if (idx >= 0 && idx < dirs.size()) showFolderPicker(dirs.get(idx));
                    else showFolderPicker(dir);
                })
                .setPositiveButton(R.string.use_this_folder, (d, w) -> {
                    Store.setFolder(MainActivity.this, dir.getAbsolutePath());
                    Store.log(MainActivity.this, "Folder: " + prettyPath(dir.getAbsolutePath()));
                    clearToolResults();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null));
    }

    // ================================================================= TOOLS page

    private void setupToolsPage() {
        findViewById(R.id.btnAddRule).setOnClickListener(v -> editRule(-1));
        renderRules();

        CheckBox sub = (CheckBox) findViewById(R.id.cbDupSub);
        sub.setChecked(Store.dupSubfolders(this));
        sub.setOnCheckedChangeListener((b, on) -> Store.setDupSubfolders(MainActivity.this, on));
        findViewById(R.id.btnDupScan).setOnClickListener(v -> scanDuplicates());
        findViewById(R.id.btnDupMove).setOnClickListener(v -> moveDuplicates());

        int days = Store.oldDays(this);
        RadioGroup rg = (RadioGroup) findViewById(R.id.rgDays);
        final EditText etDays = (EditText) findViewById(R.id.etDays);
        if (days == 30) rg.check(R.id.rbDays30);
        else if (days == 60) rg.check(R.id.rbDays60);
        else if (days == 90) rg.check(R.id.rbDays90);
        else { rg.check(R.id.rbDaysCustom); etDays.setText(String.valueOf(days)); }
        findViewById(R.id.customDaysRow).setVisibility(rg.getCheckedRadioButtonId() == R.id.rbDaysCustom ? View.VISIBLE : View.GONE);
        rg.setOnCheckedChangeListener((g, id) -> {
            findViewById(R.id.customDaysRow).setVisibility(id == R.id.rbDaysCustom ? View.VISIBLE : View.GONE);
            hideOldResult();
            int d = selectedDays();
            if (d > 0) { Store.setOldDays(MainActivity.this, d); refresh(); }
        });
        etDays.addTextChangedListener(new SimpleWatcher(() -> {
            hideOldResult();
            int d = selectedDays();
            if (d > 0) { Store.setOldDays(MainActivity.this, d); cbAutoOld.setText(getString(R.string.auto_old, d)); }
        }));
        findViewById(R.id.btnOldPreview).setOnClickListener(v -> previewOldFiles());
        findViewById(R.id.btnOldMove).setOnClickListener(v -> moveOldFiles());

        final CheckBox num = (CheckBox) findViewById(R.id.cbRenNumber);
        num.setOnCheckedChangeListener((b, on) -> {
            findViewById(R.id.renNumberRow).setVisibility(on ? View.VISIBLE : View.GONE);
            hideRenameResult();
        });
        for (int id : new int[]{R.id.etRenContains, R.id.etRenExts, R.id.etRenFind, R.id.etRenReplace,
                R.id.etRenPrefix, R.id.etRenSuffix, R.id.etRenBase, R.id.etRenStart})
            ((EditText) findViewById(id)).addTextChangedListener(new SimpleWatcher(this::hideRenameResult));
        findViewById(R.id.btnRenPreview).setOnClickListener(v -> previewRename());
        findViewById(R.id.btnRenApply).setOnClickListener(v -> applyRename());
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable r;
        SimpleWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { r.run(); }
    }

    private void clearToolResults() {
        dupChecks.clear();
        ((LinearLayout) findViewById(R.id.dupList)).removeAllViews();
        findViewById(R.id.dupSummary).setVisibility(View.GONE);
        findViewById(R.id.btnDupMove).setVisibility(View.GONE);
        hideOldResult();
        hideRenameResult();
    }

    // ---------------------------------------------------------------- custom rules

    private void renderRules() {
        LinearLayout list = (LinearLayout) findViewById(R.id.rulesList);
        list.removeAllViews();
        List<Rules.Rule> rules = Store.rules(this);
        float d = getResources().getDisplayMetrics().density;
        Palette p = Store.palette(this);
        if (rules.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(R.string.rules_empty);
            t.setTag("soft");
            t.setTextSize(14);
            t.setPadding(0, Math.round(4 * d), 0, Math.round(4 * d));
            t.setTextColor(p.textSoft);
            list.addView(t);
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            Rules.Rule r = rules.get(i);
            TextView t = new TextView(this);
            t.setText("\uD83D\uDCCC  " + describe(r) + "   \u2192   \uD83D\uDCC1 " + r.folder);
            t.setTextSize(14);
            t.setTextColor(p.text);
            t.setTag("rule");
            t.setBackground(UiTheme.round(p.field, p.stroke, 10 * d, 1.2f * d));
            int pad = Math.round(12 * d);
            t.setPadding(pad, Math.round(10 * d), pad, Math.round(10 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Math.round(6 * d);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> editRule(idx));
            list.addView(t);
        }
    }

    private String describe(Rules.Rule r) {
        if (r.kind == Rules.KIND_NAME) return getString(R.string.rule_name_contains, r.pattern);
        StringBuilder sb = new StringBuilder();
        for (String e : Rules.extensions(r.pattern)) { if (sb.length() > 0) sb.append(' '); sb.append('.').append(e); }
        return sb.toString();
    }

    private void editRule(final int index) {
        final List<Rules.Rule> rules = Store.rules(this);
        final Rules.Rule existing = index >= 0 && index < rules.size() ? rules.get(index) : null;
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * d);
        box.setPadding(pad, Math.round(8 * d), pad, 0);
        final RadioGroup kind = new RadioGroup(this);
        kind.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton rbE = new RadioButton(this), rbN = new RadioButton(this);
        rbE.setText(R.string.rule_kind_ext);
        rbN.setText(R.string.rule_kind_name);
        rbE.setId(View.generateViewId());
        rbN.setId(View.generateViewId());
        rbE.setPadding(0, 0, Math.round(16 * d), 0);
        kind.addView(rbE);
        kind.addView(rbN);
        final EditText pattern = field(R.string.rule_pattern_hint_ext);
        final EditText folder = field(R.string.rule_folder_hint);
        box.addView(kind);
        box.addView(pattern);
        box.addView(folder);
        if (existing != null) {
            (existing.kind == Rules.KIND_NAME ? rbN : rbE).setChecked(true);
            pattern.setText(existing.pattern);
            folder.setText(existing.folder);
        } else rbE.setChecked(true);
        kind.setOnCheckedChangeListener((g, id) ->
                pattern.setHint(id == rbN.getId() ? R.string.rule_pattern_hint_name : R.string.rule_pattern_hint_ext));
        if (existing != null && existing.kind == Rules.KIND_NAME) pattern.setHint(R.string.rule_pattern_hint_name);
        UiTheme.apply(box, pal);

        AlertDialog.Builder b = dialog()
                .setTitle(existing == null ? R.string.rule_add : R.string.rule_edit)
                .setView(box)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    Rules.Rule r = new Rules.Rule(rbN.isChecked() ? Rules.KIND_NAME : Rules.KIND_EXT,
                            pattern.getText().toString(), folder.getText().toString());
                    if (!r.isValid()) {
                        Toast.makeText(MainActivity.this, R.string.rule_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (existing != null) rules.set(index, r); else rules.add(r);
                    Store.setRules(MainActivity.this, rules);
                    renderRules();
                })
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) b.setNeutralButton(R.string.delete, (dlg, w) -> {
            rules.remove(index);
            Store.setRules(MainActivity.this, rules);
            renderRules();
        });
        show(b);
    }

    private EditText field(int hint) {
        float d = getResources().getDisplayMetrics().density;
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTag("field");
        int pad = Math.round(12 * d);
        e.setPadding(pad, 0, pad, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(48 * d));
        lp.topMargin = Math.round(10 * d);
        e.setLayoutParams(lp);
        return e;
    }

    // ---------------------------------------------------------------- duplicates

    Duplicates.Scan lastScan;

    private void scanDuplicates() {
        if (!checkAccess() || busy) return;
        final boolean sub = Store.dupSubfolders(this);
        clearToolResults();
        runInBackground(getString(R.string.working), app -> {
            lastScan = Duplicates.scan(new File(Store.folder(app)), sub, Sorter.DEFAULT_MIN_AGE_MS, System.currentTimeMillis());
            runOnUiThread(this::showDuplicates);
            return null;
        });
    }

    void showDuplicates() {
        Duplicates.Scan s = lastScan;
        LinearLayout list = (LinearLayout) findViewById(R.id.dupList);
        list.removeAllViews();
        dupChecks.clear();
        TextView sum = (TextView) findViewById(R.id.dupSummary);
        sum.setVisibility(View.VISIBLE);
        if (s == null || s.groups.isEmpty()) {
            sum.setText(getString(R.string.dup_none, s == null ? 0 : s.filesScanned));
            findViewById(R.id.btnDupMove).setVisibility(View.GONE);
            return;
        }
        sum.setText(getString(R.string.dup_summary, s.groups.size(), s.copyCount(), Storage.human(s.wasted())));
        File base = new File(Store.folder(this));
        float d = getResources().getDisplayMetrics().density;
        int shown = 0;
        for (Duplicates.Group g : s.groups) {
            if (shown++ >= 40) {
                TextView more = new TextView(this);
                more.setText(getString(R.string.dup_more, s.groups.size() - 40));
                more.setTag("soft");
                list.addView(more);
                break;
            }
            TextView h = new TextView(this);
            h.setText(getString(R.string.dup_group, g.files.size(), Storage.human(g.size), rel(base, g.files.get(0))));
            h.setTag("soft");
            h.setTextSize(13);
            h.setPadding(0, Math.round(10 * d), 0, 0);
            list.addView(h);
            for (File f : g.copies()) {
                CheckBox cb = new CheckBox(this);
                cb.setText(rel(base, f));
                cb.setTextSize(14);
                cb.setChecked(true);
                list.addView(cb);
                dupChecks.put(cb, f);
            }
        }
        UiTheme.apply(list, pal);
        findViewById(R.id.btnDupMove).setVisibility(View.VISIBLE);
    }

    private static String rel(File base, File f) {
        String b = base.getAbsolutePath(), p = f.getAbsolutePath();
        return p.startsWith(b + "/") ? p.substring(b.length() + 1) : f.getName();
    }

    private void moveDuplicates() {
        if (!checkAccess() || busy) return;
        final List<File> chosen = new ArrayList<>();
        for (Map.Entry<CheckBox, File> e : dupChecks.entrySet()) if (e.getKey().isChecked()) chosen.add(e.getValue());
        if (chosen.isEmpty()) return;
        clearToolResults();
        runInBackground(getString(R.string.working), app -> {
            Sorter.Result r = new Sorter.Result();
            List<String> log = new ArrayList<>();
            Duplicates.moveToDuplicates(new File(Store.folder(app)), chosen, r, log);
            String msg = Store.localized(app).getString(R.string.dup_done, r.moved);
            Store.finishAction(app, log, r, msg);
            return msg;
        });
    }

    // ---------------------------------------------------------------- old files

    private int selectedDays() {
        int id = ((RadioGroup) findViewById(R.id.rgDays)).getCheckedRadioButtonId();
        if (id == R.id.rbDays30) return 30;
        if (id == R.id.rbDays60) return 60;
        if (id == R.id.rbDays90) return 90;
        try { return Integer.parseInt(((EditText) findViewById(R.id.etDays)).getText().toString().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private void hideOldResult() {
        oldPreview = null;
        findViewById(R.id.oldResult).setVisibility(View.GONE);
        findViewById(R.id.btnOldMove).setVisibility(View.GONE);
    }

    private void previewOldFiles() {
        if (!checkAccess() || busy) return;
        final int days = selectedDays();
        if (days <= 0) { Toast.makeText(this, R.string.bad_days, Toast.LENGTH_SHORT).show(); return; }
        Store.setOldDays(this, days);
        hideOldResult();
        List<File> sel = OldFiles.select(new File(Store.folder(this)), days, System.currentTimeMillis());
        showOldPreview(sel, days);
    }

    void showOldPreview(List<File> sel, int days) {
        oldPreview = sel;
        TextView t = (TextView) findViewById(R.id.oldResult);
        StringBuilder sb = new StringBuilder(getString(R.string.old_found, sel.size(), Storage.human(OldFiles.totalSize(sel)), days));
        for (int i = 0; i < sel.size() && i < 8; i++) sb.append("\n\u2022 ").append(sel.get(i).getName());
        if (sel.size() > 8) sb.append('\n').append(getString(R.string.more_n, sel.size() - 8));
        t.setText(sb.toString());
        t.setVisibility(View.VISIBLE);
        findViewById(R.id.btnOldMove).setVisibility(sel.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void moveOldFiles() {
        if (!checkAccess() || busy || oldPreview == null) return;
        final List<File> files = oldPreview;
        hideOldResult();
        runInBackground(getString(R.string.working), app -> {
            Sorter.Result r = new Sorter.Result();
            List<String> log = new ArrayList<>();
            OldFiles.move(new File(Store.folder(app)), files, r, log);
            String msg = Store.localized(app).getString(R.string.old_done, r.moved);
            Store.finishAction(app, log, r, msg);
            return msg;
        });
    }

    // ---------------------------------------------------------------- batch rename

    private String txt(int id) { return ((EditText) findViewById(id)).getText().toString(); }

    Renamer.Spec renameSpec() {
        Renamer.Spec s = new Renamer.Spec();
        s.contains = txt(R.id.etRenContains);
        s.extensions = txt(R.id.etRenExts);
        s.find = txt(R.id.etRenFind);
        s.replace = txt(R.id.etRenReplace);
        s.prefix = txt(R.id.etRenPrefix);
        s.suffix = txt(R.id.etRenSuffix);
        s.numbering = ((CheckBox) findViewById(R.id.cbRenNumber)).isChecked();
        s.base = txt(R.id.etRenBase);
        try { s.start = Integer.parseInt(txt(R.id.etRenStart).trim()); } catch (NumberFormatException e) { s.start = 1; }
        return s;
    }

    private void hideRenameResult() {
        renPlan = null;
        findViewById(R.id.renResult).setVisibility(View.GONE);
        findViewById(R.id.btnRenApply).setVisibility(View.GONE);
    }

    void previewRename() {
        if (!checkAccess() || busy) return;
        List<Renamer.Item> plan = Renamer.plan(new File(Store.folder(this)), renameSpec());
        List<Renamer.Item> changing = new ArrayList<>();
        for (Renamer.Item it : plan) if (it.changes()) changing.add(it);
        renPlan = changing;
        TextView t = (TextView) findViewById(R.id.renResult);
        StringBuilder sb = new StringBuilder();
        if (changing.isEmpty()) sb.append(getString(R.string.ren_none));
        else sb.append(getString(R.string.ren_count, changing.size()));
        for (int i = 0; i < changing.size() && i < 25; i++) {
            Renamer.Item it = changing.get(i);
            sb.append("\n").append(it.from.getName()).append("\n   \u2192 ").append(it.newName);
            if (it.conflict) sb.append("  ").append(getString(R.string.ren_conflict));
        }
        if (changing.size() > 25) sb.append('\n').append(getString(R.string.more_n, changing.size() - 25));
        t.setText(sb.toString());
        t.setVisibility(View.VISIBLE);
        findViewById(R.id.btnRenApply).setVisibility(changing.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void applyRename() {
        if (!checkAccess() || busy || renPlan == null) return;
        final List<Renamer.Item> plan = renPlan;
        hideRenameResult();
        runInBackground(getString(R.string.working), app -> {
            Sorter.Result r = new Sorter.Result();
            List<String> log = new ArrayList<>();
            Renamer.apply(plan, r, log);
            String msg = Store.localized(app).getString(R.string.ren_done, r.moved);
            Store.finishAction(app, log, r, msg);
            return msg;
        });
    }

    // ================================================================= STATS page

    private void setupStatsPage() {
        findViewById(R.id.btnStatsRefresh).setOnClickListener(v -> loadStats());
    }

    void loadStats() {
        final TextView total = (TextView) findViewById(R.id.statsTotal);
        if (!Store.hasAllFilesAccess(this)) { total.setText(R.string.allow_to_start); return; }
        total.setText(R.string.stats_scanning);
        final File dir = new File(Store.folder(this));
        new Thread(() -> {
            final Storage.Report rep = Storage.scan(dir);
            runOnUiThread(() -> showStats(rep));
        }, "stats").start();
    }

    void showStats(Storage.Report rep) {
        TextView total = (TextView) findViewById(R.id.statsTotal);
        total.setText(rep.totalCount == 0 ? getString(R.string.stats_empty)
                : getString(R.string.stats_total, rep.totalCount, Storage.human(rep.totalBytes)));
        ((BarChartView) findViewById(R.id.chart)).setData(rep.rows, pal, getString(R.string.files_n), getString(R.string.files_one));
    }

    // ================================================================= SETTINGS page

    private void setupSettingsPage() {
        RadioGroup theme = (RadioGroup) findViewById(R.id.rgTheme);
        int t = Store.theme(this);
        theme.check(t == Palette.THEME_LIGHT ? R.id.rbLight : t == Palette.THEME_DARK ? R.id.rbDark : R.id.rbSystem);
        theme.setOnCheckedChangeListener((g, id) -> {
            int nt = id == R.id.rbLight ? Palette.THEME_LIGHT : id == R.id.rbDark ? Palette.THEME_DARK : Palette.THEME_SYSTEM;
            if (nt == Store.theme(MainActivity.this)) return;
            boolean wasDark = Store.isNight(MainActivity.this);
            Store.setTheme(MainActivity.this, nt);
            if (Store.isNight(MainActivity.this) != wasDark) recreate(); else applyTheme();
        });

        RadioGroup lang = (RadioGroup) findViewById(R.id.rgLang);
        lang.check("gu".equals(Store.lang(this)) ? R.id.rbGujarati : R.id.rbEnglish);
        lang.setOnCheckedChangeListener((g, id) -> {
            String nl = id == R.id.rbGujarati ? "gu" : "en";
            if (nl.equals(Store.lang(MainActivity.this))) return;
            Store.setLang(MainActivity.this, nl);
            recreate();                                   // every string switches right away
        });

        CheckBox manual = (CheckBox) findViewById(R.id.cbNotifyManual);
        manual.setChecked(Store.notifyManual(this));
        manual.setOnCheckedChangeListener((b, on) -> {
            Store.setNotifyManual(MainActivity.this, on);
            if (on && !Notifier.allowed(MainActivity.this)) askNotificationPermission();
        });
        findViewById(R.id.btnNotifAllow).setOnClickListener(v -> askNotificationPermission());
    }

    private void refreshNotifState() {
        boolean ok = Notifier.allowed(this);
        ((TextView) findViewById(R.id.notifState)).setText(ok ? R.string.notif_on : R.string.notif_off);
        findViewById(R.id.btnNotifAllow).setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    private void renderSwatches() {
        LinearLayout row = (LinearLayout) findViewById(R.id.swatches);
        row.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int sel = Store.accent(this);
        for (int i = 0; i < Palette.ACCENTS.length; i++) {
            final int idx = i;
            int c = Palette.ACCENTS[i];
            TextView s = new TextView(this);
            int size = Math.round(42 * d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            s.setLayoutParams(lp);
            s.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setSize(size, size);
            if (i == sel) g.setStroke(Math.round(3 * d), pal.text);
            else g.setStroke(Math.round(1 * d), pal.stroke);
            s.setBackground(g);
            s.setText(i == sel ? "\u2713" : "");
            s.setTextColor(Palette.bestTextOn(c));
            s.setTextSize(20);
            s.setTypeface(null, android.graphics.Typeface.BOLD);
            s.setContentDescription(getString(COLOR_NAMES[i]));
            s.setOnClickListener(v -> {
                Store.setAccent(MainActivity.this, idx);
                applyTheme();
                renderRules();
                if (lastScan != null && !dupChecks.isEmpty()) UiTheme.apply(findViewById(R.id.dupList), pal);
            });
            row.addView(s);
            if (i < Palette.ACCENTS.length - 1) {
                View gap = new View(this);
                gap.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1));
                row.addView(gap);
            }
        }
        ((TextView) findViewById(R.id.colorName)).setText(getString(COLOR_NAMES[sel]));
    }
}
