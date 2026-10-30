package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local-only photo preferences; excluded pictures are never modified or deleted. */
final class PhotoPreferences {
    static final String IDLE = "album_idle_minutes", INTERVAL = "album_interval_seconds";
    static final String CLOCK = "album_clock", DATE = "album_photo_date", ADAPTIVE = "photo_adaptive";
    private static final String SKIPPED = "photo_skipped_paths";
    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
    static int idleMinutes(Context ctx) { return bounded(prefs(ctx).getInt(IDLE, 5), 0, 60, 5); }
    static int intervalSeconds(Context ctx) { return bounded(prefs(ctx).getInt(INTERVAL, 15), 5, 300, 15); }
    static int bounded(int value, int min, int max, int fallback) {
        return value >= min && value <= max ? value : fallback;
    }
    static boolean adaptive(Context ctx) { return prefs(ctx).getBoolean(ADAPTIVE, true); }
    static List<File> filter(Context ctx, List<File> files) {
        Set<String> skipped = prefs(ctx).getStringSet(SKIPPED, java.util.Collections.emptySet());
        List<File> result = new ArrayList<>();
        for (File file : files) if (!skipped.contains(file.getAbsolutePath())) result.add(file);
        return result;
    }
    static List<File> albumFiles(Context ctx) {
        List<File> files = UsbWallpapers.enabled(ctx) ? filter(ctx, UsbWallpapers.images(ctx)) : new ArrayList<>();
        if (!files.isEmpty()) return files;
        return localFiles(ctx);
    }
    static List<File> localFiles(Context ctx) {
        File dir = Config.dir(ctx);
        List<File> files = filter(ctx, WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        if (files.isEmpty() && dir != null) {
            File single = new File(dir, Config.WALLPAPER_NAME);
            if (single.isFile()) files = filter(ctx, java.util.Collections.singletonList(single));
        }
        return files;
    }
    static void skip(Context ctx, File file) {
        Set<String> skipped = new HashSet<>(prefs(ctx).getStringSet(SKIPPED, java.util.Collections.emptySet()));
        skipped.add(file.getAbsolutePath());
        prefs(ctx).edit().putStringSet(SKIPPED, skipped).apply();
        changed(ctx);
    }
    static void restore(Context ctx) { prefs(ctx).edit().remove(SKIPPED).apply(); changed(ctx); }
    static int skippedCount(Context ctx) { return prefs(ctx).getStringSet(SKIPPED, java.util.Collections.emptySet()).size(); }
    static void changed(Context ctx) {
        SharedPreferences prefs = prefs(ctx);
        prefs.edit().putLong(Config.SETTINGS_REVISION_KEY, prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
