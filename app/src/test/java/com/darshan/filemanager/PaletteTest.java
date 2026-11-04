package com.darshan.filemanager;

import static org.junit.Assert.*;

import org.junit.Test;

/** Theme + app colour rules: every accent stays readable in Light and Dark. */
public class PaletteTest {

    @Test
    public void systemThemeFollowsThePhone() {
        assertTrue(Palette.isDark(Palette.THEME_SYSTEM, true));
        assertFalse(Palette.isDark(Palette.THEME_SYSTEM, false));
        assertTrue(Palette.isDark(Palette.THEME_DARK, false));
        assertFalse(Palette.isDark(Palette.THEME_LIGHT, true));
    }

    @Test
    public void maroonIsTheDefaultBrandColour() {
        assertEquals(0xFF7B1E3A, Palette.ACCENTS[0]);
        assertEquals("maroon", Palette.ACCENT_KEYS[0]);
        assertEquals(0xFFF7B500, Palette.ACCENTS[1]);
        assertEquals(6, Palette.ACCENTS.length);
        assertEquals(0, Palette.clampAccent(99));
        assertEquals(0, Palette.clampAccent(-1));
    }

    @Test
    public void goldGetsDarkTextAndMaroonGetsWhite() {
        assertEquals(Palette.INK, new Palette(false, 1).onAccent);
        assertEquals(Palette.WHITE, new Palette(false, 0).onAccent);
        assertEquals(Palette.WHITE, new Palette(true, 2).onAccent);
    }

    @Test
    public void everyAccentIsReadableInBothThemes() {
        for (boolean dark : new boolean[]{false, true}) {
            for (int i = 0; i < Palette.ACCENTS.length; i++) {
                Palette p = new Palette(dark, i);
                String what = (dark ? "dark " : "light ") + Palette.ACCENT_KEYS[i];
                assertTrue(what + " button text", Palette.contrast(p.onAccent, p.accent) >= 4.5);
                assertTrue(what + " titles", Palette.contrast(p.accentText, p.card) >= 4.5);
                assertTrue(what + " body text", Palette.contrast(p.text, p.card) >= 7);
                assertTrue(what + " body text on background", Palette.contrast(p.text, p.bg) >= 7);
                assertTrue(what + " soft text", Palette.contrast(p.textSoft, p.card) >= 4.5);
            }
        }
    }

    @Test
    public void contrastMathMatchesWcag() {
        assertEquals(21.0, Palette.contrast(0xFF000000, 0xFFFFFFFF), 0.01);
        assertEquals(1.0, Palette.contrast(0xFF7B1E3A, 0xFF7B1E3A), 0.001);
        int fixed = Palette.readableOn(0xFFF7B500, 0xFFFFFFFF, 4.5);
        assertTrue(Palette.contrast(fixed, 0xFFFFFFFF) >= 4.5);
        assertEquals(0xFF7B1E3A, Palette.readableOn(0xFF7B1E3A, 0xFFFFFFFF, 4.5));   // already fine: unchanged
    }
}
