package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

public class StorageTest {

    @Test
    public void totalsPerCategoryIncludingSubFoldersBiggestFirst() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.jpg", "1234567890");
        TestFiles.make(dir, "Images/b.png", "12345");
        TestFiles.make(dir, "movie.mp4", "12345678901234567890");
        TestFiles.make(dir, "notes.pdf", "123");
        TestFiles.make(dir, "weird.xyz", "12");
        TestFiles.make(dir, ".hidden.jpg", "1234567890");
        Storage.Report rep = Storage.scan(dir);
        assertEquals(5, rep.totalCount);
        assertEquals(40, rep.totalBytes);
        assertEquals(4, rep.rows.size());
        assertEquals("Videos", rep.rows.get(0).category);
        assertEquals(20, rep.rows.get(0).bytes);
        assertEquals("Images", rep.rows.get(1).category);
        assertEquals(2, rep.rows.get(1).count);
        assertEquals(15, rep.rows.get(1).bytes);
        assertEquals("PDF", rep.rows.get(2).category);
        assertEquals("Others", rep.rows.get(3).category);
    }

    @Test
    public void emptyFolder() throws Exception {
        Storage.Report rep = Storage.scan(TestFiles.tmpDir());
        assertEquals(0, rep.totalCount);
        assertTrue(rep.rows.isEmpty());
    }

    @Test
    public void humanSizes() {
        assertEquals("512 B", Storage.human(512));
        assertEquals("1.0 KB", Storage.human(1024));
        assertEquals("1.5 MB", Storage.human(1536L * 1024));
        assertEquals("250 MB", Storage.human(250L * 1024 * 1024));
        assertEquals("2.0 GB", Storage.human(2L * 1024 * 1024 * 1024));
    }
}
