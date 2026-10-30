package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

/** Expand square app artwork into a full colour card, keeping the original logo large and undistorted. */
final class IconCard {
    static Drawable make(Resources resources, Drawable icon) {
        if (icon == null) return null;
        Drawable draw = icon.getConstantState() == null ? icon
                : icon.getConstantState().newDrawable(resources).mutate();
        Rect saved = new Rect(draw.getBounds());
        Bitmap sample = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        draw.setBounds(0, 0, 48, 48);
        draw.draw(new Canvas(sample));
        Map<Integer, Integer> counts = new HashMap<>();
        int colour = 0xFF27364A, best = 0;
        for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
            if (x > 5 && x < 42 && y > 5 && y < 42) continue;
            int pixel = sample.getPixel(x, y);
            if ((pixel >>> 24) < 220) continue;
            int count = counts.getOrDefault(pixel, 0) + 1;
            counts.put(pixel, count);
            if (count > best) { best = count; colour = pixel | 0xFF000000; }
        }
        sample.recycle();
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(colour);
        draw.setBounds(70, 0, 250, 180);
        draw.draw(canvas);
        draw.setBounds(saved);
        return new BitmapDrawable(resources, bitmap);
    }
}
