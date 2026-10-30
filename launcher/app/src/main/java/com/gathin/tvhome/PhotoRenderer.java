package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Sampled, EXIF-aware rendering. Portrait originals stay intact over a small blurred backdrop. */
final class PhotoRenderer {
    static final class Frame {
        final Bitmap bitmap;
        final String date;
        final Bitmap foreground;
        final Bitmap preview;
        Frame(Bitmap bitmap, Bitmap foreground, String date, Bitmap preview) {
            this.bitmap = bitmap; this.foreground = foreground; this.date = date; this.preview = preview;
        }
        void recycle() { bitmap.recycle(); if (foreground != null) foreground.recycle(); if (preview != null) preview.recycle(); }
    }

    static Frame decode(File file, int width, int height, int dimColor, boolean adaptive, boolean home) {
        return decode(file, width, height, dimColor, adaptive, home, 2);
    }

    static Frame decode(File file, int width, int height, int dimColor, boolean adaptive, boolean home, int homeRows) {
        return decode(file,width,height,dimColor,adaptive,home,homeRows,null);
    }

    static Frame album(File poster, File original, int width, int height, File cache) {
        File cached = null;
        try {
            cached = AlbumPreviewCache.file(cache,original,width,height);
            if (cached.isFile()) {
                Frame hit = decode(cached,width,height,0,true,false,2,null);
                if (hit != null) { cached.setLastModified(System.currentTimeMillis()); return hit; }
                cached.delete();
            }
        } catch (Exception ignored) { }
        return decode(poster,width,height,0,true,false,2,cached);
    }

    static Frame home(File original, int width, int height, int dimColor, boolean adaptive, int rows, File cache) {
        File cached = null;
        try {
            String key = "home1:"+original.getAbsolutePath()+':'+original.length()+':'+original.lastModified()
                    +':'+width+':'+height+':'+dimColor+':'+adaptive+':'+rows;
            cached = TvPhotoCache.file(cache,original,"home",key);
            if (cached.isFile()) {
                Frame hit = cachedHome(cached,width,height);
                if (hit != null) { cached.setLastModified(System.currentTimeMillis()); return hit; }
                cached.delete();
            }
            PhotoAsset asset = PhotoAsset.resolveStill(original,new File(cache.getParentFile(),"live_photos"));
            return decode(asset.poster,width,height,dimColor,adaptive,true,rows,cached);
        } catch (Exception | OutOfMemoryError failure) { return null; }
    }

    private static Frame cachedHome(File file, int width, int height) {
        Bitmap source = null, small = null, output = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(),options);
            if (options.outWidth != width || options.outHeight != height) return null;
            options.inJustDecodeBounds = false; source = BitmapFactory.decodeFile(file.getPath(),options);
            if (source == null) return null;
            small = Bitmap.createScaledBitmap(source,Math.min(width,960),Math.min(height,540),true);
            if (small == source) small = source.copy(Bitmap.Config.ARGB_8888,false);
            output = source.copy(Bitmap.Config.HARDWARE,false);
            if (output == null) return null;
            Frame result = new Frame(output,null,"",small); output = null; small = null; return result;
        } catch (Exception | OutOfMemoryError invalid) { return null; }
        finally { if (source != null) source.recycle(); if (small != null) small.recycle(); if (output != null) output.recycle(); }
    }

    private static Frame decode(File file, int width, int height, int dimColor, boolean adaptive, boolean home, int homeRows, File cachePoster) {
        Bitmap source = null, output = null, foreground = null, preview = null, resized = null;
        try {
            if (Thread.currentThread().isInterrupted()) return null;
            if (!file.isFile() || width <= 0 || height <= 0 || !WallpaperSize.accepted(width, height)) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            String date = "";
            Matrix matrix = new Matrix();
            try {
                ExifInterface exif = new ExifInterface(file.getPath());
                date = photoDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                    case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                    case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                    case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                    case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
                    default: break;
                }
            } catch (java.io.IOException | RuntimeException ignored) { }
            options.inSampleSize = PhotoGeometry.sample(options.outWidth, options.outHeight, width, height);
            if (!home) {
                boolean swapped = Math.abs(matrixValue(matrix, android.graphics.Matrix.MSCALE_X)) < .5f;
                int[] fitted = PhotoGeometry.retainedSize(swapped ? options.outHeight : options.outWidth,
                        swapped ? options.outWidth : options.outHeight, width, height);
                int targetW = swapped ? fitted[1] : fitted[0], targetH = swapped ? fitted[0] : fitted[1];
                options.inSampleSize = Math.max(options.inSampleSize,
                        WallpaperSize.sampleSize(options.outWidth, options.outHeight, targetW, targetH));
                int sampledW = Math.max(1, options.outWidth / options.inSampleSize);
                if (sampledW > targetW) {
                    options.inScaled = true; options.inDensity = sampledW; options.inTargetDensity = targetW;
                }
            }
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            source = BitmapFactory.decodeFile(file.getPath(), options);
            if (source == null || Thread.currentThread().isInterrupted()) return null;
            if (!matrix.isIdentity()) {
                Bitmap oriented = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
                if (oriented != source) { source.recycle(); source = oriented; }
            }
            // A blurred album backdrop contains no full-resolution detail. Retain its 96x54 texture only.
            int canvasW = home ? width : PhotoGeometry.BACKDROP_W;
            int canvasH = home ? height : PhotoGeometry.BACKDROP_H;
            output = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            RectF homeRect = null;
            if (home) {
                float[] position = FaceFraming.place(source.getWidth(), source.getHeight(), width, height,
                        homeRows, PhotoFaces.bounds(file, source));
                homeRect = new RectF(position[0], position[1], position[2], position[3]);
            }
            if (!home) {
                int previewW = canvasW, previewH = canvasH;
                canvas.drawBitmap(source, null, rect(source, previewW, previewH, false), paint);
                int[] colors = new int[previewW * previewH];
                output.getPixels(colors, 0, previewW, 0, 0, previewW, previewH);
                output.setPixels(PhotoPixels.gaussian(colors, previewW, previewH), 0, previewW, 0, 0, previewW, previewH);
                canvas.drawColor(0x20000000);
            }
            if (home) canvas.drawBitmap(source, null, homeRect, paint);
            if (!home) {
                int[] size = PhotoGeometry.retainedSize(source.getWidth(), source.getHeight(), width, height);
                resized = Bitmap.createScaledBitmap(source, size[0], size[1], true);
                if (cachePoster != null && !Thread.currentThread().isInterrupted()) saveAlbumPreview(resized,date,cachePoster);
                foreground = resized.copy(Bitmap.Config.HARDWARE, false);
                if (foreground == null) return null;
            }
            if (!home && dimColor != 0) canvas.drawColor(dimColor);
            if (home && adaptive) {
                // Text sits in two separate areas; adapt their shades without dimming the entire photo further.
                shade(canvas, width, height, true, brightness(output, true));
                shade(canvas, width, height, false, brightness(output, false));
            }
            if (home) {
                if (cachePoster != null && !Thread.currentThread().isInterrupted()) saveAlbumPreview(output,date,cachePoster);
                // Make the cold-start preview before GPU upload. No full-screen GPU readback is needed later.
                preview = Bitmap.createScaledBitmap(output, Math.min(width, 960), Math.min(height, 540), true);
                if (preview == output) preview = output.copy(Bitmap.Config.ARGB_8888, false);
                Bitmap gpu = output.copy(Bitmap.Config.HARDWARE, false);
                if (gpu != null) { output.recycle(); output = gpu; }
            }
            Frame result = new Frame(output, foreground, date, preview);
            output = null; foreground = null; preview = null;
            return result;
        } catch (Exception | OutOfMemoryError failure) {
            android.util.Log.w("TvHome.Photo", "Local photo rendering failed", failure);
            return null;
        } finally {
            if (source != null) source.recycle();
            if (foreground != null) foreground.recycle();
            if (resized != null && resized != source) resized.recycle();
            if (preview != null) preview.recycle();
            if (output != null) output.recycle();
        }
    }

    private static float matrixValue(Matrix matrix, int index) {
        float[] values = new float[9]; matrix.getValues(values); return values[index];
    }

    private static synchronized void saveAlbumPreview(Bitmap bitmap, String date, File file) {
        boolean scoped = TvPhotoCache.onUsb(file) && !file.getParentFile().canWrite();
        File part = scoped ? UsbCacheAccess.staging(file) : new File(file.getPath()+".part");
        try {
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(part)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG,92,output)) return;
            }
            if (!date.isEmpty()) {
                ExifInterface exif = new ExifInterface(part.getPath());
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,date.replace('-',':')+" 00:00:00"); exif.saveAttributes();
            }
            if (scoped) UsbCacheAccess.publish(part,file);
            else if (part.renameTo(file)) AlbumPreviewCache.trim(file);
        } catch (Exception ignored) { }
        finally { if (part.isFile()) part.delete(); }
    }

    private static RectF rect(Bitmap bitmap, int width, int height, boolean contain) {
        float[] r = PhotoGeometry.bounds(bitmap.getWidth(), bitmap.getHeight(), width, height, contain);
        return new RectF(r[0], r[1], r[2], r[3]);
    }

    private static float brightness(Bitmap bitmap, boolean top) {
        float total = 0;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 12; x++) {
            int px = Math.min(bitmap.getWidth() - 1, (x * 2 + 1) * bitmap.getWidth() / 24);
            int py = Math.min(bitmap.getHeight() - 1, (int) (bitmap.getHeight()
                    * ((top ? 0.02f : 0.72f) + y * (top ? 0.04f : 0.07f))));
            int c = bitmap.getPixel(px, py);
            total += (Color.red(c) * .2126f + Color.green(c) * .7152f + Color.blue(c) * .0722f) / 255f;
        }
        return total / 48;
    }

    private static void shade(Canvas canvas, int width, int height, boolean top, float brightness) {
        int alpha = Math.round(45 + 135 * brightness);
        Paint shade = new Paint();
        float start = top ? 0 : height * .5f, end = top ? height * .28f : height;
        shade.setShader(new LinearGradient(0, start, 0, end,
                top ? alpha << 24 : 0, top ? 0 : alpha << 24, Shader.TileMode.CLAMP));
        canvas.drawRect(0, start, width, end, shade);
    }

    static String photoDate(String raw) {
        if (raw == null) return "";
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT)
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT)).toLocalDate().toString();
        } catch (RuntimeException invalid) { return ""; }
    }
}
