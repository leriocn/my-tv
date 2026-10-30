package com.gathin.tvhome;

/** Pure crop geometry. Face coordinates are normalized bounds including a little head room. */
final class FaceFraming {
    private FaceFraming() { }
    static float[] place(int imageW, int imageH, int screenW, int screenH, int rows, float[] face) {
        if (imageW <= 0 || imageH <= 0 || screenW <= 0 || screenH <= 0) throw new IllegalArgumentException();
        if (face == null || face.length != 4) {
            return PhotoGeometry.bounds(imageW, imageH, screenW, screenH, false);
        }
        float left = screenW * .07f, right = screenW * .93f;
        float top = screenH * .205f, bottom = screenH * (rows > 1 ? .505f : .69f);
        float cover = Math.max(screenW / (float) imageW, screenH / (float) imageH);
        float[] covered = fit(imageW, imageH, screenW, screenH, cover, face, left, top, right, bottom, true);
        if (covered != null) return covered;
        // Full bleed is mandatory. If all heads cannot avoid the UI, first keep them on screen.
        covered = fit(imageW, imageH, screenW, screenH, cover, face, 0, 0, screenW, screenH, true);
        if (covered != null) return covered;
        float x = screenW * .5f - (face[0] + face[2]) * .5f * imageW * cover;
        float y = (top + bottom) * .5f - (face[1] + face[3]) * .5f * imageH * cover;
        return rect(Math.max(screenW - imageW * cover, Math.min(0, x)),
                Math.max(screenH - imageH * cover, Math.min(0, y)), imageW, imageH, cover);
    }
    private static float[] fit(int w, int h, int sw, int sh, float s, float[] f,
                               float l, float t, float r, float b, boolean cover) {
        float minX = Math.max(cover ? sw - w * s : 0, l - f[0] * w * s);
        float maxX = Math.min(cover ? 0 : sw - w * s, r - f[2] * w * s);
        float minY = Math.max(cover ? sh - h * s : 0, t - f[1] * h * s);
        float maxY = Math.min(cover ? 0 : sh - h * s, b - f[3] * h * s);
        if (minX > maxX + .001f || minY > maxY + .001f) return null;
        float preferredX = (sw - w * s) * .5f;
        float preferredY = (t + b) * .5f - (f[1] + f[3]) * .5f * h * s;
        return rect(Math.max(minX, Math.min(maxX, preferredX)), Math.max(minY, Math.min(maxY, preferredY)), w, h, s);
    }
    private static float[] rect(float x, float y, int w, int h, float s) { return new float[]{x, y, x + w * s, y + h * s}; }
}
