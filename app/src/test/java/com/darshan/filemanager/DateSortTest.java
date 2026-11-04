package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import org.junit.Test;

public class DateSortTest {

    private static long at(int y, int m, int d) {
        Calendar c = Calendar.getInstance(Locale.US);
        c.clear();
        c.set(y, m - 1, d, 12, 0, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void folderNameIsYearSlashMonthNumberAndName() {
        assertEquals("2026/11-November", Sorter.dateFolderFor(at(2026, 11, 4)));
        assertEquals("2025/01-January", Sorter.dateFolderFor(at(2025, 1, 31)));
        assertEquals("2024/02-February", Sorter.dateFolderFor(at(2024, 2, 29)));
    }

    @Test
    public void sortsByModifiedDateIntoNestedFoldersAndUndoes() throws Exception {
        File dir = TestFiles.tmpDir();
        File a = TestFiles.make(dir, "trip.jpg", "a");
        File b = TestFiles.make(dir, "notes.pdf", "b");
        File c = TestFiles.make(dir, "old.mp3", "c");
        a.setLastModified(at(2026, 11, 2));
        b.setLastModified(at(2026, 10, 15));
        c.setLastModified(at(2024, 3, 9));
        File undo = new File(TestFiles.tmpDir(), "undo.txt");
        Sorter.Result r = Sorter.sort(dir, Sorter.MODE_DATE, undo, 0, System.currentTimeMillis());
        assertEquals(3, r.moved);
        assertTrue(new File(dir, "2026/11-November/trip.jpg").isFile());
        assertTrue(new File(dir, "2026/10-October/notes.pdf").isFile());
        assertTrue(new File(dir, "2024/03-March/old.mp3").isFile());

        Sorter.Result u = Sorter.undo(undo);
        assertEquals(3, u.moved);
        assertTrue(new File(dir, "trip.jpg").isFile());
        assertFalse("year folders are removed too", new File(dir, "2026").exists());
        assertFalse(new File(dir, "2024").exists());
    }

    @Test
    public void undoKeepsAYearFolderThatAlreadyHadFiles() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "2026/keep.txt", "k");
        File a = TestFiles.make(dir, "x.jpg", "a");
        a.setLastModified(at(2026, 5, 5));
        File undo = new File(TestFiles.tmpDir(), "undo.txt");
        Sorter.sort(dir, Sorter.MODE_DATE, undo, 0, System.currentTimeMillis());
        assertTrue(new File(dir, "2026/05-May/x.jpg").isFile());
        Sorter.undo(undo);
        assertTrue(new File(dir, "2026/keep.txt").isFile());
        assertFalse(new File(dir, "2026/05-May").exists());
    }
}
