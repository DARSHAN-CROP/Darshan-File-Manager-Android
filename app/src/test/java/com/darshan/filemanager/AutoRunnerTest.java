package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

public class AutoRunnerTest {
    private static final long DAY = OldFiles.DAY_MS;
    private static final String[] EN = {"%d sorted", "%d to date folders", "%d duplicates moved", "%d old files moved", "Nothing to move"};

    @Test
    public void defaultsAreRulesPlusTypeSort() {
        AutoRunner.Options o = new AutoRunner.Options();
        assertTrue(o.rules);
        assertTrue(o.sort);
        assertEquals(Sorter.MODE_TYPE, o.sortMode);
        assertFalse(o.date);
        assertFalse(o.old);
        assertFalse(o.duplicates);
        assertEquals(Arrays.asList("type"), o.steps());          // no rules saved yet
        o.ruleList.add(new Rules.Rule(Rules.KIND_EXT, "jpg", "Photos"));
        assertEquals(Arrays.asList("rules", "type"), o.steps());
    }

    @Test
    public void stepsFollowTheTicksInSafeOrder() {
        AutoRunner.Options o = new AutoRunner.Options();
        o.ruleList.add(new Rules.Rule(Rules.KIND_EXT, "jpg", "Photos"));
        o.date = true; o.old = true; o.duplicates = true; o.sortMode = Sorter.MODE_EXTENSION;
        assertEquals(Arrays.asList("old", "duplicates", "rules", "extension", "date"), o.steps());
        o.sort = false; o.rules = false;
        assertEquals(Arrays.asList("old", "duplicates", "date"), o.steps());
    }

    @Test
    public void everythingTickedRunsAllStepsAndOneUndoReversesThem() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "ancient.doc", "old", 120 * DAY);
        TestFiles.make(dir, "song.mp3", "music!", 5 * TestFiles.HOUR);
        TestFiles.make(dir, "song (1).mp3", "music!", 2 * TestFiles.HOUR);
        TestFiles.make(dir, "Marksheet.pdf", "marks");
        TestFiles.make(dir, "pic.jpg", "pixels");
        TestFiles.make(dir, "app.apk", "apk");
        AutoRunner.Options o = new AutoRunner.Options();
        o.ruleList.add(new Rules.Rule(Rules.KIND_NAME, "marksheet", "College"));
        o.old = true; o.oldDays = 60; o.duplicates = true;
        File undo = new File(TestFiles.tmpDir(), "undo.txt");
        AutoRunner.Summary s = AutoRunner.run(dir, o, undo, 0, System.currentTimeMillis());
        assertEquals(1, s.old);
        assertEquals(1, s.duplicates);
        assertEquals(4, s.sorted);                               // marksheet (rule), song, pic, apk
        assertEquals(0, s.errors);
        assertTrue(new File(dir, "Old Files/ancient.doc").isFile());
        assertTrue(new File(dir, "Duplicates/song (1).mp3").isFile());
        assertTrue(new File(dir, "College/Marksheet.pdf").isFile());
        assertTrue(new File(dir, "Music/song.mp3").isFile());
        assertTrue(new File(dir, "Apps/app.apk").isFile());
        assertEquals("4 sorted, 1 duplicates moved, 1 old files moved", AutoRunner.summaryText(s, EN));

        assertEquals(6, Sorter.undo(undo).moved);
        assertEquals(6, dir.list().length);                     // all back, every folder it made removed
    }

    @Test
    public void dateOnlyWithRules() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.jpg", "a");
        TestFiles.make(dir, "b.txt", "b");
        AutoRunner.Options o = new AutoRunner.Options();
        o.sort = false; o.date = true;
        o.ruleList.add(new Rules.Rule(Rules.KIND_EXT, "jpg", "Photos"));
        AutoRunner.Summary s = AutoRunner.run(dir, o, null, 0, System.currentTimeMillis());
        assertEquals(1, s.sorted);
        assertEquals(1, s.dated);
        assertTrue(new File(dir, "Photos/a.jpg").isFile());
        assertEquals("1 sorted, 1 to date folders", AutoRunner.summaryText(s, EN));
    }

    @Test
    public void nothingTickedMovesNothing() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.jpg", "a");
        AutoRunner.Options o = new AutoRunner.Options();
        o.sort = false; o.rules = false;
        File undo = new File(TestFiles.tmpDir(), "undo.txt");
        AutoRunner.Summary s = AutoRunner.run(dir, o, undo, 0, System.currentTimeMillis());
        assertEquals(0, s.total());
        assertTrue(new File(dir, "a.jpg").isFile());
        assertFalse(undo.exists());
        assertEquals("Nothing to move", AutoRunner.summaryText(s, EN));
    }

    @Test
    public void missingFolderIsAnErrorNotACrash() {
        AutoRunner.Summary s = AutoRunner.run(new File("/nope/does/not/exist"), new AutoRunner.Options(), null, 0, 0);
        assertEquals(1, s.errors);
    }
}
