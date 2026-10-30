package com.gathin.tvhome;

/** Tiny preview blur. Work stays bounded to a 64 x 36 backdrop, never a full-resolution photo. */
final class PhotoPixels {
    static int[] blur(int[] pixels, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || pixels.length != width * height || radius < 0 || radius > 8) {
            throw new IllegalArgumentException("Invalid blur dimensions");
        }
        int[] out = new int[pixels.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int r = 0, g = 0, b = 0, count = 0;
            for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++) {
                for (int xx = Math.max(0, x - radius); xx <= Math.min(width - 1, x + radius); xx++) {
                    int c = pixels[yy * width + xx];
                    r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; count++;
                }
            }
            out[y * width + x] = 0xFF000000 | (r / count << 16) | (g / count << 8) | b / count;
        }
        return out;
    }
    /** Separable binomial Gaussian kernel, bounded preview only; opaque output. */
    static int[] gaussian(int[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || pixels.length != width * height) throw new IllegalArgumentException("Invalid preview");
        int[] weights = {1, 6, 15, 20, 15, 6, 1};
        int[] src = pixels.clone();
        for (int axis = 0; axis < 2; axis++) {
            int[] dst = new int[src.length];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int r = 0, g = 0, b = 0;
                for (int k = -3; k <= 3; k++) {
                    int xx = Math.max(0, Math.min(width - 1, x + (axis == 0 ? k : 0)));
                    int yy = Math.max(0, Math.min(height - 1, y + (axis == 1 ? k : 0)));
                    int c = src[yy * width + xx], w = weights[k + 3];
                    r += ((c >> 16) & 255) * w; g += ((c >> 8) & 255) * w; b += (c & 255) * w;
                }
                dst[y * width + x] = 0xFF000000 | ((r + 32) / 64 << 16) | ((g + 32) / 64 << 8) | (b + 32) / 64;
            }
            src = dst;
        }
        return src;
    }
}
