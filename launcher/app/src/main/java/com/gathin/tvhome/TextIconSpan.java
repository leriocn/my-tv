package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

/** An inline icon centred on the visible Chinese glyphs without changing the text baseline. */
final class TextIconSpan extends ReplacementSpan {
    private final Drawable icon;
    private final int size;
    private final Rect glyph = new Rect();

    TextIconSpan(Drawable icon, int size) {
        this.icon = icon; this.size = size;
        icon.setBounds(0, 0, size, size);
    }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        // Leave the font's metrics intact: an icon must not push neighbouring labels down.
        return size;
    }

    @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int y, int bottom, Paint paint) {
        paint.getTextBounds("晴", 0, 1, glyph);
        int save = canvas.save();
        canvas.translate(x, y + (glyph.top + glyph.bottom - size) / 2f);
        icon.draw(canvas); canvas.restoreToCount(save);
    }
}
