package com.darshan.filemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
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
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_STORAGE = 7;

    private View permCard;
    private TextView folderPath, status, logText;
    private Button btnDate, btnExt, btnType, btnUndo;
    private Switch swAuto;
    private RadioButton rbType, rbExt;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permCard = findViewById(R.id.permCard);
        folderPath = (TextView) findViewById(R.id.folderPath);
        status = (TextView) findViewById(R.id.status);
        logText = (TextView) findViewById(R.id.logText);
        btnDate = (Button) findViewById(R.id.btnDate);
        btnExt = (Button) findViewById(R.id.btnExt);
        btnType = (Button) findViewById(R.id.btnType);
        btnUndo = (Button) findViewById(R.id.btnUndo);
        swAuto = (Switch) findViewById(R.id.swAuto);
        rbType = (RadioButton) findViewById(R.id.rbType);
        rbExt = (RadioButton) findViewById(R.id.rbExt);

        findViewById(R.id.btnPerm).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askPermission(); }
        });
        findViewById(R.id.btnChoose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!checkAccess()) return;
                File start = new File(Store.folder(MainActivity.this));
                if (!start.isDirectory()) start = Environment.getExternalStorageDirectory();
                showFolderPicker(start);
            }
        });
        findViewById(R.id.btnDownloads).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.setFolder(MainActivity.this, Store.defaultFolder());
                refresh();
            }
        });
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doDateFolder(); }
        });
        btnExt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSort(Sorter.MODE_EXTENSION); }
        });
        btnType.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSort(Sorter.MODE_TYPE); }
        });
        btnUndo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmUndo(); }
        });
        findViewById(R.id.btnClearLog).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Store.clearLog(MainActivity.this); refresh(); }
        });

        if (Store.autoMode(this) == Sorter.MODE_EXTENSION) rbExt.setChecked(true); else rbType.setChecked(true);
        ((RadioGroup) findViewById(R.id.rgMode)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                Store.setAutoMode(MainActivity.this, id == R.id.rbExt ? Sorter.MODE_EXTENSION : Sorter.MODE_TYPE);
            }
        });
        swAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { setAuto(on); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // keep the switch honest with what the system actually has scheduled
        boolean on = Store.autoOn(this);
        if (on && !AutoSortJob.isScheduled(this)) AutoSortJob.schedule(this);
        swAuto.setOnCheckedChangeListener(null);
        swAuto.setChecked(on);
        swAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean v) { setAuto(v); }
        });
        refresh();
    }

    private void refresh() {
        boolean ok = Store.hasAllFilesAccess(this);
        permCard.setVisibility(ok ? View.GONE : View.VISIBLE);
        folderPath.setText(prettyPath(Store.folder(this)));
        btnUndo.setEnabled(!busy && Sorter.hasUndo(Store.undoLog(this)));
        btnDate.setEnabled(!busy);
        btnExt.setEnabled(!busy);
        btnType.setEnabled(!busy);
        String st = Store.lastStatus(this);
        if (!busy) status.setText(!ok ? "Allow All files access to start" : (st.isEmpty() ? "Ready" : st));
        List<String> log = Store.readLog(this);
        if (log.isEmpty()) logText.setText("No activity yet");
        else {
            Collections.reverse(log);                     // newest first
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < log.size() && i < 150; i++) { if (i > 0) sb.append('\n'); sb.append(log.get(i)); }
            logText.setText(sb.toString());
        }
    }

    private static String prettyPath(String p) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (p.equals(root)) return "Internal storage";
        if (p.startsWith(root + "/")) return "Internal storage/" + p.substring(root.length() + 1);
        return p;
    }

    // ---------------------------------------------------------------- permission

    private boolean checkAccess() {
        if (Store.hasAllFilesAccess(this)) return true;
        new AlertDialog.Builder(this)
                .setTitle("Permission needed")
                .setMessage("Darshan File Manager needs \"All files access\" to move files in your Download folder. Nothing is ever deleted.")
                .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { askPermission(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            Toast.makeText(this, "Turn ON \"Allow access to manage all files\", then come back", Toast.LENGTH_LONG).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
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
    }

    // ---------------------------------------------------------------- actions

    private void doDateFolder() {
        if (!checkAccess()) return;
        File dir = new File(Store.folder(this));
        boolean[] created = new boolean[1];
        File f = dir.isDirectory() ? Sorter.makeDateFolder(dir, new Date(), created) : null;
        String msg;
        if (f == null) msg = "Could not create the date folder";
        else msg = created[0] ? "Folder created: " + f.getName() : "Folder already exists: " + f.getName();
        Store.log(this, msg);
        Store.setLastStatus(this, msg);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void doSort(final int mode) {
        if (!checkAccess() || busy) return;
        File dir = new File(Store.folder(this));
        if (isRiskyFolder(dir)) {
            new AlertDialog.Builder(this)
                    .setTitle("Sort this folder?")
                    .setMessage(prettyPath(dir.getAbsolutePath()) + "\n\nThis is a main storage folder. Files directly inside it will be moved into new folders (sub-folders are not touched). Continue?")
                    .setPositiveButton("Sort", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { runInBackground(mode); }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            runInBackground(mode);
        }
    }

    private static boolean isRiskyFolder(File dir) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String p = dir.getAbsolutePath();
        return p.equals(root) || p.equals(root + "/Android") || p.startsWith(root + "/Android/") || p.equals("/");
    }

    private void runInBackground(final int mode) {
        busy = true;
        status.setText("Sorting…");
        refresh();
        new Thread(new Runnable() {
            @Override public void run() {
                Sorter.Result r;
                try {
                    r = Store.runSort(getApplicationContext(), mode, false);
                } catch (Throwable t) {
                    r = new Sorter.Result();
                    r.errors++;
                    Store.log(getApplicationContext(), "Error: " + t);
                }
                final Sorter.Result fr = r;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        refresh();
                        status.setText((mode == Sorter.MODE_EXTENSION ? "Extension sort: " : "Type sort: ") + fr.summary());
                        if (fr.moved == 0 && fr.errors == 0)
                            Toast.makeText(MainActivity.this, "Nothing to move, folder is already clean \uD83D\uDC4D", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "sort").start();
    }

    private void confirmUndo() {
        if (!checkAccess() || busy) return;
        new AlertDialog.Builder(this)
                .setTitle("Undo last sort?")
                .setMessage("Files moved by the last sort go back to where they were, and the empty folders it made are removed.")
                .setPositiveButton("Undo", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { runUndo(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runUndo() {
        busy = true;
        status.setText("Undoing…");
        refresh();
        new Thread(new Runnable() {
            @Override public void run() {
                final Sorter.Result r = Sorter.undo(Store.undoLog(getApplicationContext()));
                List<String> out = new ArrayList<>(r.lines);
                String s = "\u21A9 Undo: " + r.moved + (r.moved == 1 ? " file" : " files") + " moved back"
                        + (r.errors > 0 ? ", " + r.errors + " could not" : "");
                out.add(s);
                Store.log(getApplicationContext(), out);
                Store.setLastStatus(getApplicationContext(), s);
                runOnUiThread(new Runnable() {
                    @Override public void run() { busy = false; refresh(); }
                });
            }
        }, "undo").start();
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
            String m = ok ? "Auto mode ON: " + (rbExt.isChecked() ? "Extension" : "Type") + " sort every 15 min"
                          : "Auto mode could not be started";
            Store.log(this, m);
            Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
        } else {
            AutoSortJob.cancel(this);
            Store.log(this, "Auto mode OFF");
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
        if (canGoUp) names.add("\u2B06  .. (up)");
        for (File f : dirs) names.add("\uD83D\uDCC1  " + f.getName());
        if (dirs.isEmpty()) names.add("(no sub-folders)");

        new AlertDialog.Builder(this)
                .setTitle(prettyPath(dir.getAbsolutePath()))
                .setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names),
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                int idx = canGoUp ? which - 1 : which;
                                if (canGoUp && which == 0) showFolderPicker(dir.getParentFile());
                                else if (idx >= 0 && idx < dirs.size()) showFolderPicker(dirs.get(idx));
                                else showFolderPicker(dir);
                            }
                        })
                .setPositiveButton("Use this folder", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Store.setFolder(MainActivity.this, dir.getAbsolutePath());
                        Store.log(MainActivity.this, "Folder: " + prettyPath(dir.getAbsolutePath()));
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
