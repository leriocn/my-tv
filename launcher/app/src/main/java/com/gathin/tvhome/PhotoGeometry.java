package com.gathin.tvhome;

/** Image layout and sampling rules shared by the wallpaper and full-screen album. */
final class PhotoGeometry {
    static final int BACKDROP_W = 96, BACKDROP_H = 54;
    private PhotoGeometry() { }

    /** Keep only pixels that can be displayed. Never upscale a small original. */
    static int[] retainedSize(int width, int height, int screenW, int screenH) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) {
            throw new IllegalArgumentException("Invalid retained image size");
        }
        double scale = Math.min(1d, Math.min(screenW * .94d / width, screenH * .92d / height));
        return new int[]{Math.max(1, (int) Math.ceil(width * scale)),
                Math.max(1, (int) Math.ceil(height * scale))};
    }

    static float[] bounds(int imageW, int imageH, int screenW, int screenH, boolean contain) {
        if (imageW <= 0 || imageH <= 0 || screenW <= 0 || screenH <= 0) {
            throw new IllegalArgumentException("Image and screen dimensions must be positive");
        }
        float sx = screenW / (float) imageW, sy = screenH / (float) imageH;
        float scale = contain ? Math.min(sx, sy) : Math.max(sx, sy);
        float w = imageW * scale, h = imageH * scale;
        return new float[]{(screenW - w) / 2f, (screenH - h) / 2f,
                (screenW + w) / 2f, (screenH + h) / 2f};
    }

    /** Bound memory even for very tall phone pictures and very wide panoramas. */
    static int sample(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while ((long) (width / sample) * (height / sample) > 4_200_000L
                || Math.max(width / sample, height / sample) > Math.max(screenW, screenH) * 2L) {
            if (sample >= 1 << 28) break;
            sample *= 2;
        }
        return sample;
    }

    static boolean due(long lastInput, long now, long delay, boolean visible, boolean focused) {
        return visible && focused && delay > 0 && now >= lastInput && now - lastInput >= delay;
    }
}
