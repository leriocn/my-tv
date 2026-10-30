package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Read-only USB browsing, with remote navigation and local image previews. */
public final class UsbFilesActivity extends Activity {
    private File root, directory;
    private TextView title;
    private ListView list;
    private List<File> entries = new ArrayList<>();
    private boolean showingDrives, listing;
    private int listingGeneration;
    private final java.util.Set<File> folders = new java.util.HashSet<>();
    private final java.util.concurrent.ExecutorService directoryLoader = BackgroundWork.single("UsbFolders");
    private java.util.concurrent.Future<?> directoryTask;
    private Dialog previewDialog;
    private PhotoSlideView previewView;
    private PhotoRenderer.Frame previewFrame;
    private java.util.concurrent.Future<?> previewTask;
    private final java.util.concurrent.ExecutorService previews = BackgroundWork.latest("UsbPreview");

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UsbCacheAccess.initialize(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(30), dp(48), dp(20));

        title = new TextView(this);
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        View gap = new View(this); panel.addView(gap, new LinearLayout.LayoutParams(1, dp(16)));
        list = new ListView(this);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        android.widget.FrameLayout scene = GlassUi.scene(this, getIntent().getParcelableExtra("background"));
        scene.addView(panel, new android.widget.FrameLayout.LayoutParams(-1, -1)); setContentView(scene);
        list.setSelector(GlassUi.choices(this));
        list.setOnItemClickListener((parent, view, index, id) -> {
            if (listing || index < 0 || index >= entries.size()) return;
            File entry = entries.get(index);
            if (showingDrives) { root = entry; directory = entry; render(); }
            else if (index == 0) onBackPressed();
            else if (folders.contains(entry)) { directory = entry; render(); }
            else open(entry);
        });
        if (state != null && state.getString("root") != null) {
            root = new File(state.getString("root"));
            directory = new File(state.getString("directory", root.getPath()));
        }
        if (!UsbWallpapers.readable(this)) requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    @Override protected void onResume() { super.onResume(); if (list != null) render(); }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (root != null && directory != null) { out.putString("root", root.getPath()); out.putString("directory", directory.getPath()); }
        super.onSaveInstanceState(out);
    }

    private void render() {
        List<File> drives = UsbWallpapers.roots(this);
        if (root == null && drives.size() == 1) { root = drives.get(0); directory = root; }
        if (root != null && !drives.contains(root)) { root = null; directory = null; }
        showingDrives = root == null;
        int generation = ++listingGeneration;
        if (directoryTask != null) directoryTask.cancel(true);
        final File currentRoot = root, currentDirectory = directory;
        final boolean drivesOnly = showingDrives;
        listing = true;
        title.setText(drivesOnly ? getString(drives.isEmpty() ? R.string.usb_no_drive : R.string.usb_choose_drive)
                : "U 盘 /" + (directory.equals(root) ? "" : directory.getPath().substring(root.getPath().length() + 1)));
        directoryTask = directoryLoader.submit(() -> {
            List<File> found = new ArrayList<>(); List<String> labels = new ArrayList<>();
            java.util.Set<File> directories = new java.util.HashSet<>();
            if (drivesOnly) {
                for (File drive : drives) { found.add(drive); directories.add(drive); labels.add("U 盘 · " + drive.getName()); }
            } else {
                found.add(currentRoot); directories.add(currentRoot);
                labels.add(currentDirectory.equals(currentRoot) ? "返回桌面" : "上一级");
                File wallpapers = currentDirectory.equals(currentRoot) ? new File(currentRoot, "tv") : null;
                if (wallpapers != null && wallpapers.isDirectory()) { found.add(wallpapers); directories.add(wallpapers); labels.add("壁纸相册 · tv"); }
                File[] files = currentDirectory.listFiles();
                if (files != null) {
                    // Cache each directory flag once; the sort itself must not make thousands of USB stat calls.
                    for (File file : files) { if (Thread.currentThread().isInterrupted()) return; if (file.isDirectory()) directories.add(file); }
                    Arrays.sort(files, Comparator.comparing((File file) -> !directories.contains(file)).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    for (File file : files) {
                        if (!file.getName().startsWith(".") && !file.equals(wallpapers)) { found.add(file); labels.add(file.getName()); }
                    }
                }
            }
            runOnUiThread(() -> {
                if (generation != listingGeneration || isDestroyed() || isFinishing()) return;
                listing = false; entries = found; folders.clear(); folders.addAll(directories);
                list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
                    @Override public View getView(int position, View convert, ViewGroup parent) {
                        TextView row = (TextView) super.getView(position, convert, parent);
                        row.setTextColor(0xFFFFFFFF); row.setTextSize(17); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinHeight(dp(50));
                        row.setPadding(dp(14), dp(8), dp(14), dp(8)); row.setSingleLine(true); row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        int resource = !drivesOnly && position == 0 ? R.drawable.ic_month_previous
                                : directories.contains(found.get(position)) ? R.drawable.ic_usb
                                : PhotoAsset.supported(found.get(position).getName().toLowerCase(Locale.ROOT)) ? R.drawable.ic_picture : android.R.drawable.ic_media_play;
                        android.graphics.drawable.Drawable icon = getDrawable(resource).mutate(); icon.setTint(0xFFE0EAF2); icon.setBounds(0, 0, dp(24), dp(24));
                        row.setCompoundDrawablesRelative(icon, null, null, null); row.setCompoundDrawablePadding(dp(14)); return row;
                    }
                }); list.requestFocus();
            });
        });
    }

    @Override public void onBackPressed() {
        if (directory == null || directory.equals(root)) { finish(); return; }
        directory = directory.getParentFile();
        render();
    }

    private void open(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (PhotoAsset.supported(name)) { preview(file); return; }
        if (mime == null || (!mime.startsWith("video/") && !mime.startsWith("audio/"))) {
            Toast.makeText(this, R.string.usb_preview_media_only, Toast.LENGTH_SHORT).show(); return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(
                    FileProvider.getUriForFile(this, getPackageName() + ".usbfiles", file), mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception failure) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void preview(File file) {
        closePreview();
        Dialog dialog = new Dialog(this, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        PhotoSlideView image = new PhotoSlideView(this);
        image.setBackgroundColor(0xFF080C11);
        image.setContentDescription(file.getName());
        previewDialog = dialog; previewView = image;
        dialog.setContentView(image);
        dialog.setOnDismissListener(d -> { if (previewDialog == dialog) closePreview(); });
        dialog.show();
        int width = getResources().getDisplayMetrics().widthPixels, height = getResources().getDisplayMetrics().heightPixels;
        previewTask = previews.submit(() -> {
            PhotoRenderer.Frame frame = null; PhotoAsset asset = null; android.graphics.drawable.Drawable animation = null;
            try {
                asset = PhotoAsset.resolve(file, new File(getCacheDir(), "live_photos"));
                frame = PhotoRenderer.album(asset.poster,file,width,height,new File(getCacheDir(),"album_previews"));
                if (frame != null && asset.animated && !Thread.currentThread().isInterrupted()) animation = PhotoSlideView.animated(asset.poster, width, height);
            } catch (Exception | OutOfMemoryError ignored) { }
            PhotoRenderer.Frame result = frame; PhotoAsset media = asset; android.graphics.drawable.Drawable motion = animation;
            runOnUiThread(() -> {
                if (previewDialog != dialog || !dialog.isShowing() || isDestroyed()) { if (result != null) result.recycle(); return; }
                if (result == null) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show(); closePreview(); return; }
                previewFrame = result; image.show(result, media, motion, width, height);
            });
        });
    }

    private void closePreview() {
        Dialog old = previewDialog; previewDialog = null;
        if (previewTask != null) { previewTask.cancel(true); previewTask = null; }
        if (previewView != null) { previewView.clear(); previewView = null; }
        PhotoRenderer.Frame frame = previewFrame; previewFrame = null;
        if (frame != null) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(frame::recycle, 500);
        if (old != null) old.dismiss();
    }
    @Override protected void onPause() { closePreview(); super.onPause(); }
    @Override protected void onDestroy() {
        ++listingGeneration; if (directoryTask != null) directoryTask.cancel(true); directoryLoader.shutdownNow();
        closePreview(); previews.shutdownNow(); super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
