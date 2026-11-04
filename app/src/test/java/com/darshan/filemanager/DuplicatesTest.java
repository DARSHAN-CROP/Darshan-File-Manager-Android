package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class DuplicatesTest {

    @Test
    public void groupsBySizeThenContentNotByName() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "song.mp3", "same bytes here", 5 * TestFiles.HOUR);
        TestFiles.make(dir, "song (1).mp3", "same bytes here", 2 * TestFiles.HOUR);
        TestFiles.make(dir, "copy_of_song.mp3", "same bytes here", 1 * TestFiles.HOUR);
        TestFiles.make(dir, "other.mp3", "diff bytes here");     // same size, different content
        TestFiles.make(dir, "small.txt", "x");
        Duplicates.Scan s = Duplicates.scan(dir, false, 0, System.currentTimeMillis());
        assertEquals(5, s.filesScanned);
        assertEquals(1, s.groups.size());
        Duplicates.Group g = s.groups.get(0);
        assertEquals(3, g.files.size());
        assertEquals("song.mp3", g.files.get(0).getName());        // the oldest one is kept
        assertEquals(15, g.size);
        assertEquals(30, g.wasted());
        assertEquals(30, s.wasted());
        assertEquals(2, s.copyCount());
        assertEquals(Duplicates.md5(g.files.get(0)), g.hash);
        assertEquals(32, g.hash.length());
    }

    @Test
    public void emptyFilesAndFreshDownloadsAreIgnored() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.txt", "");
        TestFiles.make(dir, "b.txt", "");
        TestFiles.make(dir, "c.bin", "data", 0);
        TestFiles.make(dir, "d.bin", "data", 0);
        Duplicates.Scan s = Duplicates.scan(dir, false, 10_000, System.currentTimeMillis());
        assertTrue(s.groups.isEmpty());
    }

    @Test
    public void subFoldersOnlyWhenAskedAndNeverInsideDuplicates() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "photo.jpg", "pixels", 3 * TestFiles.HOUR);
        TestFiles.make(dir, "Images/photo.jpg", "pixels", 2 * TestFiles.HOUR);
        TestFiles.make(dir, "Duplicates/photo.jpg", "pixels");
        TestFiles.make(dir, ".hidden/photo.jpg", "pixels");
        assertTrue(Duplicates.scan(dir, false, 0, System.currentTimeMillis()).groups.isEmpty());
        Duplicates.Scan deep = Duplicates.scan(dir, true, 0, System.currentTimeMillis());
        assertEquals(1, deep.groups.size());
        assertEquals(2, deep.groups.get(0).files.size());
    }

    @Test
    public void movesCopiesToDuplicatesNeverDeletesAndUndoes() throws Exception {
        File dir = TestFiles.tmpDir();
        File keep = TestFiles.make(dir, "Report.pdf", "PDF-CONTENT", 4 * TestFiles.HOUR);
        TestFiles.make(dir, "Report (1).pdf", "PDF-CONTENT", 2 * TestFiles.HOUR);
        TestFiles.make(dir, "Images/Report.pdf", "PDF-CONTENT", 1 * TestFiles.HOUR);
        Duplicates.Scan s = Duplicates.scan(dir, true, 0, System.currentTimeMillis());
        List<File> copies = Duplicates.allCopies(s);
        assertEquals(2, copies.size());
        assertFalse(copies.contains(keep));

        Sorter.Result r = new Sorter.Result();
        List<String> log = new ArrayList<>();
        Duplicates.moveToDuplicates(dir, copies, r, log);
        assertEquals(2, r.moved);
        File dup = new File(dir, Duplicates.FOLDER);
        assertEquals(2, dup.list().length);                      // same name twice: "Report (1).pdf" kept apart
        assertTrue(keep.isFile());
        File undo = new File(TestFiles.tmpDir(), "undo.txt");
        Sorter.saveUndo(undo, log, r);
        Sorter.Result u = Sorter.undo(undo);
        assertEquals(2, u.moved);
        assertTrue(new File(dir, "Report (1).pdf").isFile());
        assertTrue(new File(dir, "Images/Report.pdf").isFile());
        assertFalse(dup.exists());
    }

    @Test
    public void userCanPickOnlySomeCopies() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.zip", "zzz", 3 * TestFiles.HOUR);
        File b = TestFiles.make(dir, "b.zip", "zzz", 2 * TestFiles.HOUR);
        File c = TestFiles.make(dir, "c.zip", "zzz", 1 * TestFiles.HOUR);
        Sorter.Result r = new Sorter.Result();
        List<File> pick = new ArrayList<>();
        pick.add(c);
        Duplicates.moveToDuplicates(dir, pick, r, new ArrayList<String>());
        assertEquals(1, r.moved);
        assertTrue(b.isFile());
        assertTrue(new File(dir, "Duplicates/c.zip").isFile());
    }
}
