package com.darshan.filemanager;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Every section in Light and Dark, Gujarati, and other app colours, rendered on a simulated
 * Android 14 phone (1179 x 2556). Images go to shots.dir (screenshots/ in the project, build/ otherwise).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotsTest {

    private static final long DAY = OldFiles.DAY_MS;
    private File dl;

    @Before
    public void demoFolder() throws Exception {
        Store.accessOverrideForTests = true;
        Context c = RuntimeEnvironment.getApplication();
        dl = new File(c.getCacheDir(), "Download");
        dl.mkdirs();
        Random rnd = new Random(7);
        Object[][] files = {
                {"Images/IMG_20261101_0930.jpg", 2_400_000, 3L}, {"Images/Screenshot_2026.png", 900_000, 5L},
                {"Images/WhatsApp Image 2026.jpg", 350_000, 9L}, {"Videos/WhatsApp Video.mp4", 6_200_000, 4L},
                {"Music/Arijit_song.mp3", 3_100_000, 20L}, {"PDF/Marksheet.pdf", 420_000, 40L},
                {"Documents/Lecture_Notes.docx", 180_000, 12L}, {"Apps/game.apk", 4_000_000, 70L},
                {"Archives/Project.zip", 1_500_000, 33L},
                {"Diploma_Result.pdf", 300_000, 95L}, {"Old_Syllabus.pdf", 250_000, 130L},
                {"IMG_20260810.jpg", 1_200_000, 86L}, {"Holiday photo.jpg", 2_400_000, 2L},
                {"notes.txt", 4_000, 64L}, {"main.c", 6_000, 1L}};
        for (Object[] f : files) {
            File file = new File(dl, (String) f[0]);
            file.getParentFile().mkdirs();
            byte[] b = new byte[(Integer) f[1]];
            if (f[0].equals("Holiday photo.jpg")) System.arraycopy(new byte[0], 0, b, 0, 0);
            else rnd.nextBytes(b);
            try (FileOutputStream o = new FileOutputStream(file)) { o.write(b); }
            file.setLastModified(System.currentTimeMillis() - (Long) f[2] * DAY);
        }
        // two real duplicates of the holiday photo
        byte[] same = java.nio.file.Files.readAllBytes(new File(dl, "Holiday photo.jpg").toPath());
        for (String n : new String[]{"Holiday photo (1).jpg", "Images/Holiday photo.jpg"}) {
            File f = new File(dl, n);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(same); }
            f.setLastModified(System.currentTimeMillis() - DAY);
        }
        Store.setFolder(c, dl.getAbsolutePath());
        List<Rules.Rule> rules = new ArrayList<>();
        rules.add(new Rules.Rule(Rules.KIND_NAME, "marksheet", "College"));
        rules.add(new Rules.Rule(Rules.KIND_EXT, "c java py", "Code Projects"));
        Store.setRules(c, rules);
        Store.log(c, "IMG_20261103.jpg  \u2192  Images");
        Store.log(c, "Diploma_Result.pdf  \u2192  PDF");
        Store.log(c, "\u2714 Type sort: 10 files moved");
        Store.setLastStatus(c, "Type sort: 10 files moved");
        Store.setAutoTick(c, Store.AUTO_DUPS, true);
        Sorter.writeLines(Store.undoLog(c), java.util.Collections.singletonList("M\t/x/a\t/x/b"));   // Undo enabled
    }

    @After
    public void reset() { Store.accessOverrideForTests = null; }

    private static void shot(MainActivity a, String name) throws Exception {
        String dir = System.getProperty("shots.dir", "build/screenshots");
        new File(dir).mkdirs();
        shadowOf(Looper.getMainLooper()).idle();
        View root = a.getWindow().getDecorView();
        int w = 1179, h = 2556;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        root.jumpDrawablesToCurrentState();     // checkbox/radio animations: draw their final state
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        try (FileOutputStream o = new FileOutputStream(new File(dir, name))) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    private MainActivity open(int theme, int accent, String lang, int tab) {
        Context c = RuntimeEnvironment.getApplication();
        Store.setTheme(c, theme);
        Store.setAccent(c, accent);
        Store.setLang(c, lang);
        Store.setTab(c, tab);
        Store.setLastStatus(c, Store.sortLabel(c, Sorter.MODE_TYPE) + ": " + Store.movedText(c, 10, 0));
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ((TextView) a.findViewById(R.id.folderPath)).setText(
                a.getString(R.string.internal_storage) + "/Download");
        ((TextView) a.findViewById(R.id.statsFolder)).setText(a.getString(R.string.internal_storage) + "/Download");
        return a;
    }

    private void fillTools(MainActivity a) {
        a.lastScan = Duplicates.scan(dl, true, 0, System.currentTimeMillis());
        a.showDuplicates();
    }

    private void fillTools2(MainActivity a) {
        ((android.widget.RadioGroup) a.findViewById(R.id.rgDays)).check(R.id.rbDays60);
        a.showOldPreview(OldFiles.select(dl, 60, System.currentTimeMillis()), 60);
        ((EditText) a.findViewById(R.id.etRenExts)).setText("jpg");
        ((CheckBox) a.findViewById(R.id.cbRenNumber)).setChecked(true);
        ((EditText) a.findViewById(R.id.etRenBase)).setText("Photo");
        a.previewRename();
        shadowOf(Looper.getMainLooper()).idle();
        final ScrollView sv = (ScrollView) a.findViewById(R.id.pageTools);
        View old = a.findViewById(R.id.btnOldPreview);
        sv.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        View card = (View) old.getParent();
        a.getWindow().getDecorView().measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2556, View.MeasureSpec.EXACTLY));
        a.getWindow().getDecorView().layout(0, 0, 1179, 2556);
        sv.scrollTo(0, card.getTop() - 30);
    }

    private void scrollToBottom(MainActivity a) {
        View d = a.getWindow().getDecorView();
        d.measure(View.MeasureSpec.makeMeasureSpec(1179, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2556, View.MeasureSpec.EXACTLY));
        d.layout(0, 0, 1179, 2556);
        ScrollView sv = (ScrollView) a.findViewById(R.id.pageTools);
        sv.scrollTo(0, sv.getChildAt(0).getHeight() - sv.getHeight());
    }

    private void fillStats(MainActivity a) {
        a.showStats(Storage.scan(dl));
    }

    @Test
    public void sortSection() throws Exception {
        shot(open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_SORT), "sort_light.png");
        shot(open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_SORT), "sort_dark.png");
    }

    @Test
    public void toolsSection() throws Exception {
        MainActivity a = open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_TOOLS);
        fillTools(a);
        assertEquals(1, a.lastScan.groups.size());
        shot(a, "tools_light.png");
        a = open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_TOOLS);
        fillTools(a);
        shot(a, "tools_dark.png");
    }

    @Test
    public void toolsSectionOldFilesAndRename() throws Exception {
        MainActivity a = open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_TOOLS);
        fillTools2(a);
        assertEquals(View.VISIBLE, a.findViewById(R.id.btnRenApply).getVisibility());
        shot(a, "tools2_light.png");
        a = open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_TOOLS);
        fillTools2(a);
        shot(a, "tools2_dark.png");
        a = open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_TOOLS);
        fillTools2(a);
        scrollToBottom(a);
        shot(a, "tools3_light.png");
        a = open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_TOOLS);
        fillTools2(a);
        scrollToBottom(a);
        shot(a, "tools3_dark.png");
    }

    @Test
    public void statsSection() throws Exception {
        MainActivity a = open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_STATS);
        fillStats(a);
        assertTrue(((BarChartView) a.findViewById(R.id.chart)).rowCount() >= 6);
        shot(a, "stats_light.png");
        a = open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_STATS);
        fillStats(a);
        shot(a, "stats_dark.png");
    }

    @Test
    public void settingsSection() throws Exception {
        shot(open(Palette.THEME_LIGHT, 0, "en", MainActivity.TAB_SETTINGS), "settings_light.png");
        shot(open(Palette.THEME_DARK, 0, "en", MainActivity.TAB_SETTINGS), "settings_dark.png");
    }

    @Test
    public void gujarati() throws Exception {
        shot(open(Palette.THEME_LIGHT, 0, "gu", MainActivity.TAB_SORT), "gujarati_sort_light.png");
        MainActivity a = open(Palette.THEME_DARK, 0, "gu", MainActivity.TAB_TOOLS);
        fillTools(a);
        shot(a, "gujarati_tools_dark.png");
        shot(open(Palette.THEME_LIGHT, 0, "gu", MainActivity.TAB_SETTINGS), "gujarati_settings_light.png");
    }

    @Test
    public void otherAppColours() throws Exception {
        shot(open(Palette.THEME_LIGHT, 1, "en", MainActivity.TAB_SORT), "accent_gold_light.png");
        MainActivity a = open(Palette.THEME_DARK, 1, "en", MainActivity.TAB_STATS);
        fillStats(a);
        shot(a, "accent_gold_dark.png");
        shot(open(Palette.THEME_LIGHT, 2, "en", MainActivity.TAB_SETTINGS), "accent_blue_light.png");
        a = open(Palette.THEME_DARK, 4, "en", MainActivity.TAB_TOOLS);
        fillTools(a);
        shot(a, "accent_purple_dark.png");
        shot(open(Palette.THEME_LIGHT, 3, "en", MainActivity.TAB_SORT), "accent_green_light.png");
        shot(open(Palette.THEME_LIGHT, 5, "en", MainActivity.TAB_SORT), "accent_orange_light.png");
    }
}
