package com.darshan.filemanager;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RulesTest {

    private static List<Rules.Rule> rules(Rules.Rule... r) { return new ArrayList<>(Arrays.asList(r)); }

    @Test
    public void extensionRuleMatchesAnyListedExtensionIgnoringCase() {
        Rules.Rule r = new Rules.Rule(Rules.KIND_EXT, ".JPG, png heic", "Photos");
        assertTrue(r.matches("IMG_1.jpg"));
        assertTrue(r.matches("a.PNG"));
        assertTrue(r.matches("x.Heic"));
        assertFalse(r.matches("notes.txt"));
        assertFalse(r.matches("jpg"));                   // a bare name is not an extension
        assertEquals(Arrays.asList("jpg", "png", "heic"), Rules.extensions(".JPG, png heic"));
    }

    @Test
    public void nameContainsRuleIsCaseInsensitive() {
        Rules.Rule r = new Rules.Rule(Rules.KIND_NAME, "Invoice", "Bills");
        assertTrue(r.matches("amazon_INVOICE_2026.pdf"));
        assertFalse(r.matches("receipt.pdf"));
    }

    @Test
    public void firstMatchingRuleWins() {
        List<Rules.Rule> list = rules(new Rules.Rule(Rules.KIND_NAME, "marksheet", "College"),
                new Rules.Rule(Rules.KIND_EXT, "pdf", "PDF Files"));
        assertEquals("College", Rules.folderFor("Marksheet_Sem4.pdf", list));
        assertEquals("PDF Files", Rules.folderFor("book.pdf", list));
        assertNull(Rules.folderFor("song.mp3", list));
        assertNull(Rules.folderFor("song.mp3", null));
    }

    @Test
    public void folderNamesCannotEscapeTheChosenFolder() {
        assertEquals("_etc_x", Rules.cleanFolder("../etc/x"));
        assertFalse(Rules.cleanFolder("../../x").contains("/"));
        assertFalse(Rules.cleanFolder("../../x").startsWith("."));
        assertEquals("Photos", Rules.cleanFolder("  Photos  "));
        assertFalse(new Rules.Rule(Rules.KIND_EXT, "", "X").isValid());
        assertFalse(new Rules.Rule(Rules.KIND_EXT, "jpg", "  ").isValid());
    }

    @Test
    public void encodeDecodeRoundTrip() {
        List<Rules.Rule> list = rules(new Rules.Rule(Rules.KIND_EXT, "jpg png", "Photos"),
                new Rules.Rule(Rules.KIND_NAME, "WhatsApp", "WA Stuff"));
        List<Rules.Rule> back = Rules.decode(Rules.encode(list));
        assertEquals(2, back.size());
        assertEquals(Rules.KIND_NAME, back.get(1).kind);
        assertEquals("WhatsApp", back.get(1).pattern);
        assertEquals("WA Stuff", back.get(1).folder);
        assertTrue(Rules.decode("garbage\nmore\tgarbage").isEmpty());
        assertTrue(Rules.decode(null).isEmpty());
    }

    @Test
    public void rulesRunBeforeTheBuiltInTypeMap() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "Marksheet.pdf", "a");
        TestFiles.make(dir, "book.pdf", "b");
        TestFiles.make(dir, "photo.jpg", "c");
        File undo = new File(dir, "../undo-" + System.nanoTime() + ".txt");
        List<Rules.Rule> list = rules(new Rules.Rule(Rules.KIND_NAME, "marksheet", "College"));
        Sorter.Result r = Sorter.sort(dir, Sorter.MODE_TYPE, list, undo, 0, System.currentTimeMillis());
        assertEquals(3, r.moved);
        assertEquals(1, r.byRule);
        assertTrue(new File(dir, "College/Marksheet.pdf").isFile());
        assertTrue(new File(dir, "PDF/book.pdf").isFile());
        assertTrue(new File(dir, "Images/photo.jpg").isFile());
        assertEquals(3, Sorter.undo(undo).moved);
        assertFalse(new File(dir, "College").exists());
    }

    @Test
    public void rulesOnlyModeLeavesUnmatchedFiles() throws Exception {
        File dir = TestFiles.tmpDir();
        TestFiles.make(dir, "a.jpg", "a");
        TestFiles.make(dir, "b.txt", "b");
        Sorter.Result r = Sorter.sort(dir, Sorter.MODE_RULES_ONLY,
                rules(new Rules.Rule(Rules.KIND_EXT, "jpg", "Photos")), null, 0, System.currentTimeMillis());
        assertEquals(1, r.moved);
        assertTrue(new File(dir, "Photos/a.jpg").isFile());
        assertTrue(new File(dir, "b.txt").isFile());
    }
}
