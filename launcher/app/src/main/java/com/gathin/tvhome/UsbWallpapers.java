package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Personal Android 8/9 setup: read pictures from removable drives' root tv folder. Never writes to a drive. */
final class UsbWallpapers {
    private static volatile Snapshot snapshot;
    private static final class Snapshot {
        final List<File> files; final String signature;
        Snapshot(List<File> files, String signature) { this.files = files; this.signature = signature; }
    }
    static final String ENABLED = "usb_wallpapers";
    static final String INTERVAL = "wallpaper_interval_minutes";
    static final String ORDER = "wallpaper_play_order";

    static boolean enabled(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, context.getResources().getBoolean(R.bool.usb_wallpapers));
    }

    static boolean supported() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P;
    }

    static boolean readable(Context context) {
        return supported() && context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    static List<File> images(Context context) {
        return new ArrayList<>(cached(context).files);
    }

    /** All file-system scans run on MainActivity's background scanner, never in a focus/animation callback. */
    static void refresh(Context context) {
        List<File> pictures = UsbWallpaperFiles.firstCollection(roots(context));
        String signature = UsbWallpaperFiles.signature(pictures);
        Snapshot previous = snapshot;
        snapshot = new Snapshot(pictures, signature);
        if (previous != null && previous.signature.equals(signature)) return;
        org.json.JSONArray entries = new org.json.JSONArray();
        try {
            for (File picture : pictures) entries.put(picture.getAbsolutePath());
            org.json.JSONObject data = new org.json.JSONObject().put("files", entries).put("signature", signature);
            File index = new File(context.getCacheDir(), "usb-photo-index.json");
            File part = new File(context.getCacheDir(), "usb-photo-index.part");
            try (java.io.OutputStream output = new java.io.FileOutputStream(part)) {
                output.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            part.renameTo(index);
        } catch (Exception ignored) { }
    }
    private static Snapshot cached(Context context) {
        Snapshot current = snapshot; if (current != null) return current;
        synchronized (UsbWallpapers.class) {
            if (snapshot != null) return snapshot;
            List<File> files = new ArrayList<>(); String signature = "pending";
            try {
                File index = new File(context.getCacheDir(), "usb-photo-index.json");
                if (index.length() <= 1024 * 1024) {
                    String data;
                    try (java.io.InputStream input = new java.io.FileInputStream(index)) {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = input.read(buffer)) != -1) { bytes.write(buffer, 0, n); if (bytes.size() > 1024 * 1024) throw new java.io.IOException("Index too large"); }
                        data = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    org.json.JSONObject json = new org.json.JSONObject(data);
                    List<File> roots = roots(context);
                    org.json.JSONArray entries = json.getJSONArray("files");
                    for (int i = 0; i < Math.min(2000, entries.length()); i++) {
                        String path = entries.getString(i);
                        for (File root : roots) if (path.startsWith(root.getAbsolutePath() + "/tv/") && !path.substring(root.getAbsolutePath().length() + 4).contains("/")) {
                            files.add(new File(path)); break;
                        }
                    }
                    signature = files.isEmpty() ? "pending" : json.getString("signature");
                }
            } catch (Exception ignored) { }
            snapshot = new Snapshot(files, signature); return snapshot;
        }
    }

    static List<File> roots(Context context) {
        List<File> roots = new ArrayList<>();
        if (!readable(context)) return roots;
        StorageManager manager = context.getSystemService(StorageManager.class);
        if (manager == null) return roots;
        try {
            for (StorageVolume volume : manager.getStorageVolumes()) {
                String state = volume.getState();
                String uuid = volume.getUuid();
                if (volume.isRemovable() && uuid != null && uuid.matches("[A-Za-z0-9-]+")
                        && (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) {
                    roots.add(new File("/storage", uuid));
                }
            }
            roots.sort(Comparator.comparing(File::getPath));
            return roots;
        } catch (SecurityException ignored) {
            return new ArrayList<>();
        }
    }

    static String signature(Context context) {
        return enabled(context) ? cached(context).signature : "disabled";
    }
}
