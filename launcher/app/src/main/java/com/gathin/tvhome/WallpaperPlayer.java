package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final List<File> files;
    private final List<File> fallbackFiles;
    private List<File> pendingFiles;
    private boolean triedFallback;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = BackgroundWork.single("HomePhotos");
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Random random = new Random();
    private final int width;
    private final int height;
    private final boolean adaptive;
    private final int favoriteRows;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private Bitmap menuPreview;
    private static final class Rendered {
        final Bitmap bitmap, menu;
        Rendered(Bitmap bitmap, Bitmap menu) { this.bitmap = bitmap; this.menu = menu; }
        void recycle() { bitmap.recycle(); if (menu != null) menu.recycle(); }
    }
    private ImageView outgoing;
    private final Motion.Policy motion;
    private boolean active;
    private final Runnable shrinkHidden = this::trim;
    private volatile boolean closed;
    private int consecutiveFailures;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        motion = new Motion.Policy(context);
        files = new ArrayList<>(PhotoPreferences.filter(context, config.wallpapers));
        File themeDir = Config.dir(context);
        fallbackFiles = config.wallpaperEnabled && themeDir != null
                ? PhotoPreferences.filter(context, WallpaperRotation.images(new File(themeDir, "wallpapers"))) : Collections.emptyList();
        interval = config.wallpaperRotationMinutes * 60_000L;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        adaptive = PhotoPreferences.adaptive(context);
        favoriteRows = FavoriteShelf.rows(Favorites.load(context).size());
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            loadPreview();
            showFirst();
        }
    }

    /** Decode photos and find face positions off the UI thread, including the first wallpaper. */
    private void showFirst() {
        if (closed) return;
        loading = true;
        decoder.execute(this::decodeFirst);
    }

    private void decodeFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = new ArrayList<>(playable());
        List<File> failed = new ArrayList<>();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null && failed.size() < 8 && !closed && !Thread.currentThread().isInterrupted()) {
            Rendered bitmap = decode(first);
            if (bitmap != null) {
                if (closed) { bitmap.recycle(); return; }
                String name = first.getName();
                main.post(() -> {
                    loading = false;
                    if (closed || !active) { bitmap.recycle(); return; }
                    files.removeAll(failed);
                    show(bitmap); root.setBackground(null); displayed = name; showCaption(false);
                    if (!displayed.equals(previous)) preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                    update();
                });
                return;
            }
            failed.add(first);
            playable.remove(first);
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
        main.post(() -> {
            loading = false;
            if (closed || !active) return;
            files.removeAll(failed);
            if (useFallback()) showFirst();
            else files.clear(); // Keep the last safe frame; never spin through thousands of corrupt files.
        });
    }

    private boolean useFallback() {
        if (triedFallback || fallbackFiles.isEmpty()) return false;
        triedFallback = true;
        files.clear();
        files.addAll(fallbackFiles);
        return true;
    }

    void start() {
        main.removeCallbacks(shrinkHidden);
        active = true;
        update();
    }

    void stop() {
        active = false;
        main.removeCallbacks(shrinkHidden); main.postDelayed(shrinkHidden, 1500);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return files;
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
        main.removeCallbacks(shrinkHidden);
        clearOutgoing();
        image.setImageDrawable(null);
        if (caption != null) caption.animate().cancel();
        Bitmap old = current; current = null;
        if (menuPreview != null) { menuPreview.recycle(); menuPreview = null; }
        if (old != null) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(old::recycle, 500);
    }
    void trim() {
        if (active || closed) return;
        if (current != null && current.getWidth() <= 960 && current.getHeight() <= 540) return;
        // Decode the small preview in the worker. Keep the visible last frame until its replacement is ready.
        loadPreview();
    }

    private void loadPreview() {
        if (closed) return;
        Bitmap expected = current;
        decoder.execute(() -> {
            Bitmap cached = null;
            try {
                File preview = new File(root.getContext().getCacheDir(), "home-preview.jpg");
                cached = android.graphics.BitmapFactory.decodeFile(preview.getPath());
            } catch (RuntimeException | OutOfMemoryError ignored) { }
            Bitmap loaded = cached;
            main.post(() -> {
                if (loaded == null) return;
                if (closed || current != expected || (expected != null && active)) { loaded.recycle(); return; }
                clearOutgoing(); current = loaded; displayed = ""; image.setImageBitmap(loaded);
                if (expected != null) main.postDelayed(expected::recycle, 500);
            });
        });
    }

    void update() {
        if (!active || closed || loading) return;
        if (pendingFiles != null) {
            files.clear(); files.addAll(pendingFiles); pendingFiles = null;
            preferences.edit().putString("collection", collectionKey(files)).apply();
            triedFallback = false; consecutiveFailures = 0; displayed = "";
        }
        if (files.isEmpty() && !useFallback()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Rendered bitmap = decode(next);
            if (closed) { if (bitmap != null) bitmap.recycle(); return; }
            main.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    if (++consecutiveFailures >= 8) {
                        consecutiveFailures = 0;
                        if (!useFallback()) { files.clear(); return; }
                    }
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                consecutiveFailures = 0;
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                update();
            });
        });
    }

    /** Collection refresh never clears the visible bitmap or recreates Home. */
    void replaceFiles(List<File> collection) {
        pendingFiles = new ArrayList<>(PhotoPreferences.filter(root.getContext(), collection));
        update();
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getAbsolutePath()).append(';');
        return key.toString();
    }

    /** Puts a picture on screen and releases the previous one after a frame has drawn the new one. */
    private void show(Rendered rendered) {
        Bitmap bitmap = rendered.bitmap;
        Bitmap old = current;
        if (menuPreview != null) menuPreview.recycle();
        menuPreview = rendered.menu;
        clearOutgoing();
        current = bitmap;
        image.setImageBitmap(bitmap);
        int duration = motion.duration(320);
        if (old != null && old != bitmap && !old.isRecycled() && duration > 0 && image.getParent() == root) {
            outgoing = new ImageView(root.getContext()); outgoing.setScaleType(ImageView.ScaleType.CENTER_CROP);
            outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); outgoing.setImageBitmap(old);
            root.addView(outgoing, root.indexOfChild(image) + 1, new FrameLayout.LayoutParams(-1, -1));
            ImageView previous = outgoing;
            previous.animate().alpha(0).setDuration(duration).withEndAction(() -> { if (outgoing == previous) clearOutgoing(); }).start();
        }
        if (old != null && old != bitmap) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(old::recycle, 500);
    }

    private void clearOutgoing() {
        if (outgoing == null) return;
        ImageView old = outgoing; outgoing = null; old.animate().cancel(); old.setImageDrawable(null); root.removeView(old);
    }

    File displayedFile() { return WallpaperRotation.find(files, displayed); }

    /** Tiny blurred copy of the exact current Home crop; no second photo decode or face scan. */
    Bitmap blurredPreview() {
        if (menuPreview == null || menuPreview.isRecycled()) return null;
        try { return menuPreview.copy(Bitmap.Config.ARGB_8888, false); }
        catch (RuntimeException | OutOfMemoryError unavailable) { return null; }
    }

    private Rendered decode(File file) {
        PhotoRenderer.Frame frame = null; Bitmap menu = null;
        try {
            frame = PhotoRenderer.home(file,width,height,dimColor,adaptive,favoriteRows,
                    new File(root.getContext().getCacheDir(),"home_previews"));
            if (frame == null) return null;
            if (!closed && frame.preview != null) {
                savePreview(frame.preview);
                menu = Bitmap.createScaledBitmap(frame.preview, 96, 54, true);
                int[] colors = new int[96 * 54]; menu.getPixels(colors, 0, 96, 0, 0, 96, 54);
                menu.setPixels(PhotoPixels.gaussian(colors, 96, 54), 0, 96, 0, 0, 96, 54);
            }
            Rendered result = new Rendered(frame.bitmap, menu); menu = null;
            if (frame.preview != null) frame.preview.recycle();
            frame = null; return result;
        } catch (Exception | OutOfMemoryError failure) { return null; }
        finally { if (frame != null) frame.recycle(); if (menu != null) menu.recycle(); }
    }

    private void savePreview(Bitmap bitmap) {
        File part = new File(root.getContext().getCacheDir(), "home-preview.part");
        try {
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(part)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) return;
            }
            if (!closed) part.renameTo(new File(root.getContext().getCacheDir(), "home-preview.jpg"));
        } catch (Exception | OutOfMemoryError ignored) { }
        finally {
            if (part.isFile()) part.delete();
        }
    }
}
