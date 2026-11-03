package com.darshan.filemanager;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowEnvironment;

/** Runs the real screen on a simulated Android 14 (Robolectric) and saves screenshots. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {

    private static void shot(MainActivity a, String name) throws Exception {
        String dir = System.getProperty("shots.dir", "build/screenshots");
        new File(dir).mkdirs();
        View root = a.getWindow().getDecorView();
        int w = 1179, h = 2556;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        try (FileOutputStream o = new FileOutputStream(new File(dir, name))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    @Test
    public void opensWithoutPermissionAndOffersAllFilesAccess() throws Exception {
        setAllFiles(false);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View card = a.findViewById(R.id.permCard);
        assertEquals(View.VISIBLE, card.getVisibility());
        // sorting without permission must not crash; it asks instead
        a.findViewById(R.id.btnType).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        a.findViewById(R.id.btnPerm).performClick();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, next.getAction());
        assertEquals("package:com.darshan.filemanager", next.getDataString());
        shot(a, "1_no_permission.png");
    }

    @Test
    public void sortsUndoesAndSchedulesAutoMode() throws Exception {
        setAllFiles(true);
        Context c = androidx_free_context();
        File dl = new File(c.getCacheDir(), "Download");
        dl.mkdirs();
        String[] names = {"Diploma_Result.pdf", "IMG_20261103.jpg", "Screenshot_2026.png", "Lecture_Notes.docx",
                "WhatsApp Video.mp4", "Arijit_song.mp3", "Project.zip", "game.apk", "main.c", "Marksheet.pdf",
                "movie.mkv.crdownload"};
        for (String n : names) {
            File f = new File(dl, n);
            new FileOutputStream(f).close();
            f.setLastModified(System.currentTimeMillis() - 3_600_000L);
        }
        Store.setFolder(c, dl.getAbsolutePath());
        Sorter.Result r = Store.runSort(c, Sorter.MODE_TYPE, false);
        assertEquals(10, r.moved);
        assertTrue(new File(dl, "movie.mkv.crdownload").isFile());
        assertTrue(new File(dl, "PDF/Marksheet.pdf").isFile());

        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = ctl.get();
        assertEquals(View.GONE, a.findViewById(R.id.permCard).getVisibility());
        assertTrue(a.findViewById(R.id.btnUndo).isEnabled());
        String status = ((TextView) a.findViewById(R.id.status)).getText().toString();
        assertTrue(status, status.contains("10 files moved"));

        ((Switch) a.findViewById(R.id.swAuto)).setChecked(true);
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertEquals(1, js.getAllPendingJobs().size());
        JobInfo job = js.getAllPendingJobs().get(0);
        assertTrue(job.isPeriodic());
        assertEquals(15 * 60 * 1000L, job.getIntervalMillis());
        assertTrue(job.isPersisted());
        ctl.pause().resume();
        shadowOf(Looper.getMainLooper()).idle();
        ((TextView) a.findViewById(R.id.folderPath)).setText("Internal storage/Download");
        shot(a, "2_after_type_sort.png");

        Sorter.Result u = Sorter.undo(Store.undoLog(c));
        assertEquals(10, u.moved);
        assertTrue(new File(dl, "Marksheet.pdf").isFile());
        assertFalse(new File(dl, "PDF").exists());

        ((Switch) a.findViewById(R.id.swAuto)).setChecked(false);
        assertEquals(0, js.getAllPendingJobs().size());
    }

    private static void setAllFiles(boolean on) {
        // off: the real Android check runs (and must not crash); on: simulate the granted permission
        Store.accessOverrideForTests = on ? Boolean.TRUE : null;
    }

    private static Context androidx_free_context() {
        return org.robolectric.RuntimeEnvironment.getApplication();
    }
}
