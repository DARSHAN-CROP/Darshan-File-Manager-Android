package com.darshan.filemanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Simple horizontal bar chart: one row per category (name · count, size, bar). */
public class BarChartView extends View {

    private final List<Storage.Usage> rows = new ArrayList<>();
    private Palette pal = new Palette(false, 0);
    private String filesFmt = "%d files", fileOne = "1 file";
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), bold = new Paint(Paint.ANTI_ALIAS_FLAG),
            soft = new Paint(Paint.ANTI_ALIAS_FLAG), bar = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public BarChartView(Context c) { this(c, null); }

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        bold.setTextSize(14 * sp);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(13 * sp);
        soft.setTextSize(12.5f * sp);
        soft.setTextAlign(Paint.Align.RIGHT);
    }

    public void setData(List<Storage.Usage> data, Palette p, String filesFormat, String oneFile) {
        fileOne = oneFile;
        rows.clear();
        if (data != null) rows.addAll(data);
        pal = p;
        filesFmt = filesFormat;
        requestLayout();
        invalidate();
    }

    public void setPalette(Palette p) { pal = p; invalidate(); }

    public int rowCount() { return rows.size(); }

    private float rowH() { return 52 * d; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = Math.round(rows.size() * rowH() + (rows.isEmpty() ? 0 : 4 * d));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (rows.isEmpty()) return;
        long max = 1;
        for (Storage.Usage u : rows) max = Math.max(max, u.bytes);
        bold.setColor(pal.text);
        text.setColor(pal.textSoft);
        soft.setColor(pal.text);
        track.setColor(pal.barTrack);
        float w = getWidth(), y = 0, r = 5 * d, barH = 10 * d;
        for (int i = 0; i < rows.size(); i++) {
            Storage.Usage u = rows.get(i);
            float base = y + 20 * d;
            c.drawText(u.category, 0, base, bold);
            float nameW = bold.measureText(u.category);
            c.drawText("  ·  " + (u.count == 1 ? fileOne : String.format(java.util.Locale.US, filesFmt, u.count)), nameW, base, text);
            c.drawText(Storage.human(u.bytes), w, base, soft);
            float top = y + 30 * d;
            c.drawRoundRect(new RectF(0, top, w, top + barH), r, r, track);
            float fw = Math.max(barH, (float) (w * ((double) u.bytes / max)));
            // biggest category in full accent, the rest a little lighter so the order reads at a glance
            bar.setColor(Palette.blend(pal.accent, pal.card, Math.min(0.45, i * 0.07)));
            c.drawRoundRect(new RectF(0, top, fw, top + barH), r, r, bar);
            y += rowH();
        }
    }
}
