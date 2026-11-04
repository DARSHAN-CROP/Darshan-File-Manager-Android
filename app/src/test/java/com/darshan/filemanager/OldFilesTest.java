package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class OldFilesTest {
    private static final long DAY = OldFiles.DAY_MS;

    @Test
    public void selectsOnlyFilesOlderThanNDays() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "ancient.pdf", "a", 200 * DAY);
        TestFiles.make(dir, "old.jpg", "bb", 61 * DAY);
        TestFiles.make(dir, "recent.mp4", "c", 59 * DAY);
        TestFiles.make(dir, "today.txt", "d", TestFiles.HOUR);
        TestFiles.make(dir, ".hidden_old", "e", 300 * DAY);
        TestFiles.make(dir, "Folder/inner_old.txt", "f", 300 * DAY);
        long now = System.currentTimeMillis();
        List<File> sixty = OldFiles.select(dir, 60, now);
        assertEquals(2, sixty.size());
        assertEquals("ancient.pdf", sixty.get(0).getName());
        assertEquals("old.jpg", sixty.get(1).getName());
        assertEquals(3, OldFiles.totalSize(sixty));
        assertEquals(1, OldFiles.select(dir, 90, now).size());
        assertEquals(3, OldFiles.select(dir, 30, now).size());
        assertEquals(3, OldFiles.select(dir, 1, now).size());   // the 1-hour-old file stays
        assertTrue(OldFiles.select(dir, 0, now).isEmpty());
    }

    @Test
    public void movesToOldFilesAndUndoes() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.pdf", "a", 100 * DAY);
        TestFiles.make(dir, "b.pdf", "b", 100 * DAY);
        TestFiles.make(dir, "c.pdf", "c", 2 * DAY);
        Sorter.Result r = new Sorter.Result();
        List<String> log = new ArrayList<>();
        OldFiles.move(dir, OldFiles.select(dir, 30, System.currentTimeMillis()), r, log);
        assertEquals(2, r.moved);
        assertTrue(new File(dir, "Old Files/a.pdf").isFile());
        assertTrue(new File(dir, "c.pdf").isFile());
        File undo = new File(TestFiles.tmpDir(), "u.txt");
        Sorter.saveUndo(undo, log, r);
        assertEquals(2, Sorter.undo(undo).moved);
        assertTrue(new File(dir, "a.pdf").isFile());
        assertFalse(new File(dir, OldFiles.FOLDER).exists());
    }
}
