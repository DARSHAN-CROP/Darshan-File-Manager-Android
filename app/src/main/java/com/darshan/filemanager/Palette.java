package com.darshan.filemanager;

/**
 * Theme + accent colours as plain ints (ARGB), no Android imports, so the contrast rules are unit-tested.
 * Light / Dark / System; System follows the phone's night mode (Configuration.uiMode).
 */
public final class Palette {

    public static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;

    /** Accent "app colours". Index 0 (Maroon) is the brand colour and the default. */
    public static final String[] ACCENT_KEYS = {"maroon", "gold", "blue", "green", "purple", "orange"};
    public static final int[] ACCENTS = {0xFF7B1E3A, 0xFFF7B500, 0xFF1E63D6, 0xFF1B7F45, 0xFF6A3DB8, 0xFFE8650A};

    public static final int WHITE = 0xFFFFFFFF;
    public static final int INK = 0xFF1C1B1F;          // dark text used on light accents (Gold, Orange)
    public static final int GOLD = 0xFFD4A63E;          // brand gold stripe
    public static final int MAROON = 0xFF7B1E3A;

    public final boolean dark;
    public final int accent, onAccent, accentText, accentPressed, highlight;
    public final int bg, card, stroke, text, textSoft, field, permBg, permStroke, barTrack;

    public Palette(boolean dark, int accentIndex) {
        this.dark = dark;
        int a = ACCENTS[clampAccent(accentIndex)];
        accent = a;
        onAccent = bestTextOn(a);
        if (dark) {
            bg = 0xFF121214; card = 0xFF1E1E23; stroke = 0xFF33333B; text = 0xFFECECF1; textSoft = 0xFFA6A6B0;
            field = 0xFF2A2A31; permBg = 0xFF3A2F14; permStroke = 0xFFB88A28; barTrack = 0xFF2C2C33;
        } else {
            bg = 0xFFF6F3F0; card = 0xFFFFFFFF; stroke = 0xFFE6E0DA; text = 0xFF28282F; textSoft = 0xFF5E5E68;
            field = 0xFFF2F2F5; permBg = 0xFFFFF4DC; permStroke = 0xFFD4A63E; barTrack = 0xFFEFEBE7;
        }
        accentText = readableOn(a, card, 4.5);   // titles / links in the accent colour, readable on cards
        accentPressed = blend(a, onAccent == WHITE ? 0xFF000000 : WHITE, 0.22);
        // the thin stripe under the header: brand gold, or maroon when the accent is already gold-ish
        highlight = (a == ACCENTS[1] || a == ACCENTS[5]) ? MAROON : GOLD;
    }

    public static int clampAccent(int i) { return i < 0 || i >= ACCENTS.length ? 0 : i; }

    /** System theme: dark when the phone's night-mode bits say yes. */
    public static boolean isDark(int themeMode, boolean systemNight) {
        if (themeMode == THEME_DARK) return true;
        if (themeMode == THEME_LIGHT) return false;
        return systemNight;
    }

    // ------------------------------------------------------------------ colour maths (WCAG 2)

    static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 255) + 0.7152 * channel((argb >> 8) & 255) + 0.0722 * channel(argb & 255);
    }

    public static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** White or near-black, whichever reads better on this background (dark text on Gold). */
    public static int bestTextOn(int bg) {
        return contrast(WHITE, bg) >= contrast(INK, bg) ? WHITE : INK;
    }

    public static int blend(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int g = (int) Math.round(((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The colour, lightened or darkened just enough to reach the contrast ratio on bg. */
    public static int readableOn(int color, int bg, double ratio) {
        if (contrast(color, bg) >= ratio) return color;
        int toward = luminance(bg) < 0.5 ? WHITE : 0xFF000000;
        for (int i = 1; i <= 20; i++) {
            int c = blend(color, toward, i / 20.0);
            if (contrast(c, bg) >= ratio) return c;
        }
        return toward;
    }

    public static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }
}
