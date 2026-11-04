package com.darshan.filemanager;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Looper;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Settings persistence, language switch, theme prefs and the Auto mode notification on a simulated phone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppSettingsTest {

    private Context c() { return RuntimeEnvironment.getApplication(); }

    @After
    public void reset() { Store.accessOverrideForTests = null; }

    @Test
    public void defaults() {
        Context c = c();
        assertEquals(Palette.THEME_SYSTEM, Store.theme(c));
        assertEquals(0, Store.accent(c));                       // Maroon
        assertEquals("en", Store.lang(c));
        assertTrue(Store.autoTick(c, Store.AUTO_RULES));
        assertTrue(Store.autoTick(c, Store.AUTO_SORT));
        assertFalse(Store.autoTick(c, Store.AUTO_DATE));
        assertFalse(Store.autoTick(c, Store.AUTO_OLD));
        assertFalse(Store.autoTick(c, Store.AUTO_DUPS));
        assertEquals(30, Store.oldDays(c));
        assertFalse(Store.notifyManual(c));
    }

    @Test
    public void themeAndAccentArePersisted() {
        Context c = c();
        Store.setTheme(c, Palette.THEME_DARK);
        Store.setAccent(c, 1);
        assertEquals(Palette.THEME_DARK, Store.theme(c));
        Palette p = Store.palette(c);
        assertTrue(p.dark);
        assertEquals(0xFFF7B500, p.accent);
        assertEquals(Palette.INK, p.onAccent);
        Store.setTheme(c, Palette.THEME_LIGHT);
        assertFalse(Store.palette(c).dark);
        Store.setAccent(c, 42);
        assertEquals(0, Store.accent(c));
    }

    @Test
    public void systemThemeFollowsDayMode() {
        Store.setTheme(c(), Palette.THEME_SYSTEM);
        assertFalse(Store.isNight(c()));
    }

    @Test
    @Config(qualifiers = "night")
    public void systemThemeFollowsNightMode() {
        Store.setTheme(c(), Palette.THEME_SYSTEM);
        assertTrue(Store.isNight(c()));
        Store.setTheme(c(), Palette.THEME_LIGHT);
        assertFalse(Store.isNight(c()));
    }

    @Test
    public void autoTicksArePersisted() {
        Context c = c();
        Store.setAutoTick(c, Store.AUTO_DUPS, true);
        Store.setAutoTick(c, Store.AUTO_SORT, false);
        Store.setOldDays(c, 45);
        Store.setAutoTick(c, Store.AUTO_OLD, true);
        List<Rules.Rule> rules = new ArrayList<>();
        rules.add(new Rules.Rule(Rules.KIND_EXT, "jpg", "Photos"));
        Store.setRules(c, rules);
        AutoRunner.Options o = Store.autoOptions(c);
        assertTrue(o.duplicates);
        assertFalse(o.sort);
        assertTrue(o.old);
        assertEquals(45, o.oldDays);
        assertEquals(1, o.ruleList.size());
        assertEquals("Photos", o.ruleList.get(0).folder);
    }

    @Test
    public void languageSwitchChangesEveryScreenString() {
        Store.accessOverrideForTests = true;
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("Sort by Type", ((Button) a.findViewById(R.id.btnType)).getText().toString().replaceAll("^\\S+\\s+", ""));
        a.selectTab(MainActivity.TAB_SETTINGS);
        ((RadioButton) a.findViewById(R.id.rbGujarati)).setChecked(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("gu", Store.lang(c()));

        MainActivity g = Robolectric.buildActivity(MainActivity.class).setup().get();
        String type = ((Button) g.findViewById(R.id.btnType)).getText().toString();
        assertTrue(type, type.contains("પ્રકાર"));
        assertEquals("સેટિંગ્સ", g.getString(R.string.tab_settings));
        // text made outside the screen (Auto mode, notifications) follows the language too
        assertEquals("5 files ખસેડી", Store.movedText(c(), 5, 0));
        String[] fmt = Store.summaryFormats(c());
        assertTrue(fmt[2], fmt[2].contains("duplicates"));

        Store.setLang(c(), "en");
        assertEquals("5 files moved", Store.movedText(c(), 5, 0));
        assertEquals("1 file moved, 2 errors", Store.movedText(c(), 1, 2));
    }

    @Test
    public void gujaratiHasEveryEnglishString() throws Exception {
        // every key in values/strings.xml must exist in values-gu/strings.xml
        File res = new File("src/main/res");
        if (!res.isDirectory()) res = new File("app/src/main/res");
        String en = new String(java.nio.file.Files.readAllBytes(new File(res, "values/strings.xml").toPath()), "UTF-8");
        String gu = new String(java.nio.file.Files.readAllBytes(new File(res, "values-gu/strings.xml").toPath()), "UTF-8");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("name=\"(\\w+)\"").matcher(en);
        int n = 0;
        while (m.find()) { n++; assertTrue("missing in Gujarati: " + m.group(1), gu.contains("name=\"" + m.group(1) + "\"")); }
        assertTrue(n > 100);
    }

    @Test
    public void autoRunNotifiesWithASummary() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS");
        File dl = new File(app.getCacheDir(), "Download");
        dl.mkdirs();
        for (String n : new String[]{"a.jpg", "b.pdf", "c.mp3"}) {
            File f = new File(dl, n);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(n.getBytes()); }
            f.setLastModified(System.currentTimeMillis() - 3_600_000L);
        }
        File dup = new File(dl, "a copy.jpg");
        try (FileOutputStream o = new FileOutputStream(dup)) { o.write("a.jpg".getBytes()); }
        dup.setLastModified(System.currentTimeMillis() - 60_000L);
        Store.setFolder(app, dl.getAbsolutePath());
        Store.setAutoTick(app, Store.AUTO_DUPS, true);

        AutoRunner.Summary s = Store.runAuto(app);
        assertEquals(3, s.sorted);
        assertEquals(1, s.duplicates);
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        List<Notification> all = shadowOf(nm).getAllNotifications();
        assertEquals(1, all.size());
        String text = all.get(0).extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertEquals("3 sorted, 1 duplicates moved", text);
        assertNotNull(nm.getNotificationChannel(Notifier.CHANNEL));
        assertTrue(Store.lastStatus(app).contains("3 sorted"));
        // and one Undo brings everything back
        assertEquals(4, Sorter.undo(Store.undoLog(app)).moved);
    }

    @Test
    public void manualSortNotifiesOnlyWhenAsked() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS");
        File dl = new File(app.getCacheDir(), "Download2");
        dl.mkdirs();
        File f = new File(dl, "x.jpg");
        new FileOutputStream(f).close();
        f.setLastModified(System.currentTimeMillis() - 3_600_000L);
        Store.setFolder(app, dl.getAbsolutePath());
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        Store.runSort(app, Sorter.MODE_TYPE, false);
        assertEquals(0, shadowOf(nm).getAllNotifications().size());
        Sorter.undo(Store.undoLog(app));
        Store.setNotifyManual(app, true);
        Store.runSort(app, Sorter.MODE_TYPE, false);
        assertEquals(1, shadowOf(nm).getAllNotifications().size());
    }

    @Test
    public void noNotificationWithoutPermission() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        shadowOf(app).denyPermissions("android.permission.POST_NOTIFICATIONS");
        assertFalse(Notifier.allowed(app));
        Notifier.show(app, true, "x");   // must not crash
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        assertEquals(0, shadowOf(nm).getAllNotifications().size());
    }

    @Test
    public void tabsSwitchSections() {
        Store.accessOverrideForTests = true;
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.tabView(MainActivity.TAB_TOOLS).performClick();
        assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.pageTools).getVisibility());
        assertEquals(android.view.View.GONE, a.findViewById(R.id.pageSort).getVisibility());
        a.tabView(MainActivity.TAB_SETTINGS).performClick();
        assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.pageSettings).getVisibility());
        assertEquals(MainActivity.TAB_SETTINGS, Store.tab(a));
        TextView about = (TextView) a.findViewById(R.id.aboutText);
        assertTrue(about.getText().toString(), about.getText().toString().contains("2.0"));
    }

    @Test
    public void accentChangeRepaintsAtOnce() {
        Store.accessOverrideForTests = true;
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.selectTab(MainActivity.TAB_SETTINGS);
        android.view.ViewGroup sw = (android.view.ViewGroup) a.findViewById(R.id.swatches);
        sw.getChildAt(2 * 2).performClick();                    // Blue (swatches are separated by spacers)
        assertEquals(2, Store.accent(a));
        int header = ((android.graphics.drawable.ColorDrawable) a.findViewById(R.id.header).getBackground()).getColor();
        assertEquals(0xFF1E63D6, header);
    }
}
