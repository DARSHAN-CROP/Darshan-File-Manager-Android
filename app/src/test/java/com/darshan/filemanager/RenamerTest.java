package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class RenamerTest {

    private static Renamer.Spec spec() { return new Renamer.Spec(); }

    @Test
    public void prefixSuffixAndFindReplace() {
        Renamer.Spec s = spec();
        s.prefix = "2026_";
        s.suffix = "_final";
        s.find = "img";
        s.replace = "Photo";
        assertEquals("2026_Photo_1234_final.jpg", Renamer.newName("IMG_1234.jpg", 0, s));
        assertEquals("2026_README_final", Renamer.newName("README", 0, s));      // no extension stays without one
    }

    @Test
    public void numberingLikePhoto001() {
        Renamer.Spec s = spec();
        s.numbering = true;
        s.base = "Photo";
        assertEquals("Photo_001.jpg", Renamer.newName("IMG_9.jpg", 0, s));
        assertEquals("Photo_012.png", Renamer.newName("x.png", 11, s));
        s.start = 7; s.digits = 2;
        assertEquals("Photo_07.jpg", Renamer.newName("a.jpg", 0, s));
        s.base = "";
        assertEquals("trip_07.jpg", Renamer.newName("trip.jpg", 0, s));
    }

    @Test
    public void findIsLiteralNotARegex() {
        Renamer.Spec s = spec();
        s.find = "(1)";
        s.replace = "";
        assertEquals("file .pdf", Renamer.newName("file (1).pdf", 0, s));
        s.find = ".";
        s.replace = "$1";
        assertEquals("a$1b.txt", Renamer.newName("a.b.txt", 0, s));
    }

    @Test
    public void filterByNameAndExtension() {
        Renamer.Spec s = spec();
        s.contains = "whatsapp";
        s.extensions = "jpg, .png";
        assertTrue(Renamer.matches("WhatsApp Image 1.jpg", s));
        assertFalse(Renamer.matches("WhatsApp Video.mp4", s));
        assertFalse(Renamer.matches("camera.jpg", s));
    }

    @Test
    public void previewShowsConflictsAndApplyNeverOverwrites() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "b.jpg", "1");
        TestFiles.make(dir, "c.jpg", "2");
        TestFiles.make(dir, "Photo_001.jpg", "existing");
        TestFiles.make(dir, "notes.txt", "3");
        Renamer.Spec s = spec();
        s.extensions = "jpg";
        s.contains = "";
        s.numbering = true;
        s.base = "Photo";
        List<Renamer.Item> plan = Renamer.plan(dir, s);
        assertEquals(3, plan.size());                    // notes.txt is filtered out
        assertEquals("Photo_001.jpg", plan.get(0).from.getName());
        assertFalse(plan.get(0).changes());              // already has its name
        assertEquals("b.jpg", plan.get(1).from.getName());
        assertEquals("Photo_002.jpg", plan.get(1).newName);
        assertEquals("Photo_003.jpg", plan.get(2).newName);

        TestFiles.make(dir, "Photo_003.jpg", "appeared after preview");
        Sorter.Result r = new Sorter.Result();
        List<String> log = new ArrayList<>();
        Renamer.apply(plan, r, log);
        assertEquals(2, r.moved);
        assertEquals(1, r.skipped);
        assertTrue(new File(dir, "Photo_002.jpg").isFile());
        assertTrue(new File(dir, "Photo_003 (1).jpg").isFile());     // the newer file was not overwritten

        File undo = new File(TestFiles.tmpDir(), "u.txt");
        Sorter.saveUndo(undo, log, r);
        assertEquals(2, Sorter.undo(undo).moved);
        assertTrue(new File(dir, "b.jpg").isFile());
        assertTrue(new File(dir, "c.jpg").isFile());
        assertTrue(new File(dir, "Photo_003.jpg").isFile());
    }

    @Test
    public void planMarksNameClashes() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.txt", "1");
        TestFiles.make(dir, "x_a.txt", "2");
        Renamer.Spec s = spec();
        s.contains = "a";
        s.prefix = "x_";
        List<Renamer.Item> plan = Renamer.plan(dir, s);
        assertEquals("x_a.txt", plan.get(0).newName);
        assertTrue(plan.get(0).conflict);
    }

    @Test
    public void caseOnlyRenameWorks() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "photo.JPG", "1");
        Renamer.Spec s = spec();
        s.find = "photo";
        s.replace = "Photo";
        List<Renamer.Item> plan = Renamer.plan(dir, s);
        assertEquals("Photo.JPG", plan.get(0).newName);
        Sorter.Result r = new Sorter.Result();
        Renamer.apply(plan, r, new ArrayList<String>());
        assertEquals(1, r.moved);
        assertEquals("Photo.JPG", dir.list()[0]);
    }
}
