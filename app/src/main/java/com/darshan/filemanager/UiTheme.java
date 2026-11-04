package com.darshan.filemanager;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

/** Paints the view tree from a Palette. Views say what they are with android:tag (card, title, btn, ...). */
final class UiTheme {
    private UiTheme() {}

    static GradientDrawable round(int fill, int stroke, float radius, float strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(radius);
        g.setColor(fill);
        if (strokeW > 0) g.setStroke(Math.max(1, Math.round(strokeW)), stroke);
        return g;
    }

    static Drawable button(float d, int fill, int pressed, int disabled, int stroke) {
        StateListDrawable s = new StateListDrawable();
        float r = 12 * d, sw = stroke == 0 ? 0 : 1.5f * d;
        s.addState(new int[]{-android.R.attr.state_enabled}, round(disabled, stroke, r, sw));
        s.addState(new int[]{android.R.attr.state_pressed}, round(pressed, stroke, r, sw));
        s.addState(new int[]{}, round(fill, stroke, r, sw));
        return s;
    }

    static ColorStateList textStates(int normal, int disabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{disabled, normal});
    }

    static ColorStateList checkedStates(int on, int off) {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{on, off});
    }

    static void apply(View v, Palette p) {
        float d = v.getResources().getDisplayMetrics().density;
        Object tagObj = v.getTag();
        String tag = tagObj instanceof String ? (String) tagObj : "";
        switch (tag) {
            case "card": v.setBackground(round(p.card, p.stroke, 14 * d, 1.5f * d)); break;
            case "perm": v.setBackground(round(p.permBg, p.permStroke, 14 * d, 1.5f * d)); break;
            case "title": case "status": case "link": ((TextView) v).setTextColor(p.accentText); break;
            case "text": ((TextView) v).setTextColor(p.text); break;
            case "soft": ((TextView) v).setTextColor(p.textSoft); break;
            case "btn": {
                TextView b = (TextView) v;
                b.setBackground(button(d, p.accent, p.accentPressed, Palette.blend(p.accent, p.card, 0.55), 0));
                b.setTextColor(textStates(p.onAccent, Palette.withAlpha(p.onAccent, 0x99)));
                break;
            }
            case "btnGold": {
                TextView b = (TextView) v;
                int gold = Palette.ACCENTS[1];
                b.setBackground(button(d, gold, Palette.blend(gold, 0xFF000000, 0.15), Palette.blend(gold, p.card, 0.5), 0));
                b.setTextColor(Palette.INK);
                break;
            }
            case "btn2": {
                TextView b = (TextView) v;
                int pressed = Palette.blend(p.field, p.text, 0.12);
                b.setBackground(button(d, p.field, pressed, Palette.blend(p.field, p.card, 0.6), p.stroke));
                b.setTextColor(textStates(p.text, Palette.withAlpha(p.text, 0x66)));
                break;
            }
            case "field": {
                EditText e = (EditText) v;
                e.setBackground(round(p.field, p.stroke, 10 * d, 1.2f * d));
                e.setTextColor(p.text);
                e.setHintTextColor(p.textSoft);
                break;
            }
            default: break;
        }
        if (v instanceof CompoundButton) {
            CompoundButton cb = (CompoundButton) v;
            if (tag.isEmpty()) cb.setTextColor(p.text);
            if (cb instanceof Switch) {
                Switch sw = (Switch) cb;
                sw.setThumbTintList(checkedStates(p.accent, p.dark ? 0xFFB9B9C2 : 0xFFF1F1F3));
                sw.setTrackTintList(checkedStates(Palette.withAlpha(p.accent, 0x88), Palette.withAlpha(p.textSoft, 0x66)));
            } else {
                cb.setButtonTintList(checkedStates(p.accentText, p.textSoft));
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), p);
        }
    }
}
