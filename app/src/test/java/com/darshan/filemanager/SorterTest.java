package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Date;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SorterTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File dl, undo;
    private static final long OLD = 60_000L;
    private long now;

    @Before public void setUp() throws IOException {
        dl = tmp.newFolder("Download");
        undo = new File(tmp.getRoot(), "private/last_sort_undo.txt");
        now = System.currentTimeMillis();
    }

    private File file(String name, String content) throws IOException {
        File f = new File(dl, name);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(content.getBytes(StandardCharsets.UTF_8)); }
        f.setLastModified(now - OLD);
        return f;
    }
    private String read(File f) throws IOException { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    @Test public void typeSortPutsFilesInCategories() throws IOException {
        file("photo.JPG", "a"); file("clip.mp4", "b"); file("song.mp3", "c"); file("notes.pdf", "d");
        file("marks.xlsx", "e"); file("backup.zip", "f"); file("game.apk", "g"); file("main.py", "h");
        file("weird.xyz", "i"); file("README", "j"); file("font.ttf", "k");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_TYPE, undo, Sorter.DEFAULT_MIN_AGE_MS, now);
        assertEquals(11, r.moved); assertEquals(0, r.errors);
        assertTrue(new File(dl, "Images/photo.JPG").isFile());
        assertTrue(new File(dl, "Videos/clip.mp4").isFile());
        assertTrue(new File(dl, "Music/song.mp3").isFile());
        assertTrue(new File(dl, "PDF/notes.pdf").isFile());
        assertTrue(new File(dl, "Documents/marks.xlsx").isFile());
        assertTrue(new File(dl, "Archives/backup.zip").isFile());
        assertTrue(new File(dl, "Apps/game.apk").isFile());
        assertTrue(new File(dl, "Code/main.py").isFile());
        assertTrue(new File(dl, "Others/weird.xyz").isFile());
        assertTrue(new File(dl, "Others/README").isFile());
        assertTrue(new File(dl, "Fonts/font.ttf").isFile());
        assertEquals("11 files moved", r.summary());
    }

    @Test public void extensionSortUsesUppercaseAndNoExtension() throws IOException {
        file("a.pdf", "1"); file("b.PDF", "2"); file("c.Jpg", "3"); file("Makefile", "4"); file(".gitignore", "5");
        file("John.Smith resume", "6"); file("archive.tar.gz", "7");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_EXTENSION, undo, Sorter.DEFAULT_MIN_AGE_MS, now);
        assertEquals(6, r.moved);
        assertTrue(new File(dl, "PDF/a.pdf").isFile());
        assertTrue(new File(dl, "PDF/b.PDF").isFile());
        assertTrue(new File(dl, "JPG/c.Jpg").isFile());
        assertTrue(new File(dl, "NO_EXTENSION/Makefile").isFile());
        assertTrue(new File(dl, "NO_EXTENSION/John.Smith resume").isFile());
        assertTrue(new File(dl, "TAR.GZ/archive.tar.gz").isFile());
        assertTrue("hidden file must stay", new File(dl, ".gitignore").isFile());
    }

    @Test public void nameClashNeverOverwrites() throws IOException {
        new File(dl, "PDF").mkdir();
        File existing = new File(dl, "PDF/resume.pdf");
        try (FileOutputStream o = new FileOutputStream(existing)) { o.write("OLD".getBytes()); }
        File existing1 = new File(dl, "PDF/resume (1).pdf");
        try (FileOutputStream o = new FileOutputStream(existing1)) { o.write("OLD1".getBytes()); }
        file("resume.pdf", "NEW");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_EXTENSION, undo, 0, now);
        assertEquals(1, r.moved);
        assertEquals("OLD", read(existing));
        assertEquals("OLD1", read(existing1));
        assertEquals("NEW", read(new File(dl, "PDF/resume (2).pdf")));
        assertFalse(new File(dl, "resume.pdf").exists());
    }

    @Test public void skipsPartialHiddenFoldersAndFreshFiles() throws IOException {
        file("movie.mp4.crdownload", "x"); file("a.tmp", "x"); file("b.part", "x"); file(".pending-1700000000-video.mp4", "x");
        file(".trashed-1700-pic.jpg", "x"); file(".nomedia", "x"); file("big.iso.partial", "x"); file("~$report.docx", "x");
        new File(dl, "MyFolder").mkdir(); new File(dl, "MyFolder/inside.pdf").createNewFile();
        File fresh = file("justnow.pdf", "x"); fresh.setLastModified(now - 2_000);   // still being written
        file("done.pdf", "x");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_TYPE, undo, Sorter.DEFAULT_MIN_AGE_MS, now);
        assertEquals(1, r.moved);
        assertTrue(new File(dl, "PDF/done.pdf").isFile());
        for (String n : new String[]{"movie.mp4.crdownload", "a.tmp", "b.part", ".pending-1700000000-video.mp4",
                ".trashed-1700-pic.jpg", ".nomedia", "big.iso.partial", "~$report.docx", "justnow.pdf", "MyFolder/inside.pdf"})
            assertTrue(n + " must not move", new File(dl, n).isFile());
    }

    @Test public void undoRestoresEverythingAndRemovesEmptyFolders() throws IOException {
        file("x.jpg", "1"); file("y.pdf", "2"); file("z.zip", "3");
        new File(dl, "Images").mkdir();                     // existed before: must survive undo
        File keep = new File(dl, "Images/old.png"); keep.createNewFile();
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_TYPE, undo, 0, now);
        assertEquals(3, r.moved);
        assertTrue(Sorter.hasUndo(undo));
        Sorter.Result u = Sorter.undo(undo);
        assertEquals(3, u.moved); assertEquals(0, u.errors);
        assertEquals("1", read(new File(dl, "x.jpg")));
        assertEquals("2", read(new File(dl, "y.pdf")));
        assertEquals("3", read(new File(dl, "z.zip")));
        assertFalse("PDF folder made by the sort is removed", new File(dl, "PDF").exists());
        assertFalse(new File(dl, "Archives").exists());
        assertTrue("pre-existing folder stays", keep.isFile());
        assertFalse("undo log consumed", Sorter.hasUndo(undo));
        assertEquals(0, Sorter.undo(undo).moved);
    }

    @Test public void undoDoesNotOverwriteANewFileWithTheSameName() throws IOException {
        file("cv.pdf", "FIRST");
        Sorter.sort(dl, Sorter.MODE_TYPE, undo, 0, now);
        file("cv.pdf", "SECOND");                           // new download with the same name arrived
        Sorter.Result u = Sorter.undo(undo);
        assertEquals(1, u.moved);
        assertEquals("SECOND", read(new File(dl, "cv.pdf")));
        assertEquals("FIRST", read(new File(dl, "cv (1).pdf")));
    }

    @Test public void emptyRunKeepsPreviousUndo() throws IOException {
        file("a.mp3", "1");
        Sorter.sort(dl, Sorter.MODE_TYPE, undo, 0, now);
        Sorter.Result again = Sorter.sort(dl, Sorter.MODE_TYPE, undo, 0, now);
        assertEquals(0, again.moved);
        assertTrue(Sorter.hasUndo(undo));
        assertEquals(1, Sorter.undo(undo).moved);
        assertTrue(new File(dl, "a.mp3").isFile());
    }

    @Test public void secondSortDoesNotTouchSortedFolders() throws IOException {
        file("a.jpg", "1"); file("b.jpg", "2");
        Sorter.sort(dl, Sorter.MODE_TYPE, undo, 0, now);
        file("c.jpg", "3");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_EXTENSION, undo, 0, now);
        assertEquals(1, r.moved);
        assertTrue(new File(dl, "Images/a.jpg").isFile());
        assertTrue(new File(dl, "JPG/c.jpg").isFile());
    }

    @Test public void dateFolderIsDdMmYyyyAndIdempotent() {
        Calendar c = Calendar.getInstance(); c.set(2026, Calendar.NOVEMBER, 10);
        Date d = c.getTime();
        assertEquals("10-11-2026", Sorter.todayFolderName(d));
        boolean[] made = new boolean[1];
        File f = Sorter.makeDateFolder(dl, d, made);
        assertTrue(f.isDirectory()); assertTrue(made[0]);
        File g = Sorter.makeDateFolder(dl, d, made);
        assertEquals(f, g); assertFalse(made[0]);
    }

    @Test public void bigTypeMapCoversCommonExtensions() {
        for (String e : new String[]{"jpg", "JPEG", "png", "gif", "webp", "heic", "heif", "svg", "ico", "tiff", "raw", "cr2", "nef", "dng", "psd", "ai", "avif", "bmp"}) assertEquals(e, "Images", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"mp4", "MKV", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mpeg", "vob"}) assertEquals(e, "Videos", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"mp3", "wav", "aac", "flac", "ogg", "m4a", "opus", "amr", "wma", "mid", "aiff"}) assertEquals(e, "Music", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"pdf", "PDF"}) assertEquals(e, "PDF", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "csv", "ods", "ppt", "pptx", "odp", "pages"}) assertEquals(e, "Documents", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"epub", "mobi", "azw3", "djvu", "cbz", "fb2"}) assertEquals(e, "Ebooks", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz", "cab"}) assertEquals(e, "Archives", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"apk", "xapk", "apks", "aab", "obb", "exe", "msi", "deb", "jar"}) assertEquals(e, "Apps", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"py", "java", "kt", "c", "cpp", "h", "cs", "js", "html", "css", "php", "json", "xml", "sql", "sh", "dart", "go", "rs", "swift", "ipynb", "yaml"}) assertEquals(e, "Code", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"ttf", "otf", "woff", "woff2"}) assertEquals(e, "Fonts", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"fig", "sketch", "xd", "cdr", "indd", "blend", "stl", "dwg"}) assertEquals(e, "Design", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"vcf", "ics"}) assertEquals(e, "Contacts_Calendar", Sorter.typeFolderFor("file." + e));
        for (String e : new String[]{"torrent"}) assertEquals(e, "Torrents", Sorter.typeFolderFor("file." + e));
        assertTrue("300+ extensions known", Sorter.knownExtensionCount() >= 300);
        assertEquals("Others", Sorter.typeFolderFor("file.qwerty"));
        assertEquals("Others", Sorter.typeFolderFor("README"));
        assertEquals("Others", Sorter.typeFolderFor("file."));
        assertEquals("Archives", Sorter.typeFolderFor("backup.tar.gz"));
    }

    @Test public void extensionSortHandlesAnyExtensionAndOddNames() throws IOException {
        assertEquals("XYZ123", Sorter.extFolderFor("data.xyz123"));
        assertEquals("TAR.BZ2", Sorter.extFolderFor("src.TAR.BZ2"));
        assertEquals("NO_EXTENSION", Sorter.extFolderFor("file.thisisaverylongext"));
        assertEquals("NO_EXTENSION", Sorter.extFolderFor("trailingdot."));
        file("દર્શન ફોટો.jpg", "gu");      // Gujarati name
        file("emoji \ud83d\ude00 party.MP4", "em");
        file("my.file.name.pdf", "p");
        file("weird name (copy) [1].Docx", "d");
        file("a.tar.gz", "t");
        Sorter.Result r = Sorter.sort(dl, Sorter.MODE_EXTENSION, undo, 0, now);
        assertEquals(5, r.moved); assertEquals(0, r.errors);
        assertEquals("gu", read(new File(dl, "JPG/દર્શન ફોટો.jpg")));
        assertTrue(new File(dl, "MP4/emoji \ud83d\ude00 party.MP4").isFile());
        assertTrue(new File(dl, "PDF/my.file.name.pdf").isFile());
        assertTrue(new File(dl, "DOCX/weird name (copy) [1].Docx").isFile());
        assertTrue(new File(dl, "TAR.GZ/a.tar.gz").isFile());
        assertEquals(5, Sorter.undo(undo).moved);
    }

    @Test public void unreadableFolderReportsErrorNotCrash() {
        Sorter.Result r = Sorter.sort(new File(dl, "does-not-exist"), Sorter.MODE_TYPE, undo, 0, now);
        assertEquals(1, r.errors); assertEquals(0, r.moved);
    }

    @Test public void copyFallbackMovesExactlyOneCopy() throws IOException {
        File src = file("a.bin", "DATA");
        File dst = new File(tmp.newFolder("other"), "a.bin");
        assertTrue(Sorter.moveFile(src, dst));
        assertFalse(src.exists()); assertEquals("DATA", read(dst));
        File src2 = file("b.bin", "B");
        assertFalse("never overwrite an existing target", Sorter.moveFile(src2, dst));
        assertEquals("DATA", read(dst)); assertTrue(src2.exists());
    }
}
