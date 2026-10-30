package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen local album. Only Home starts the idle timer; it never appears over another app. */
public final class AlbumActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = BackgroundWork.single("AlbumPhotos");
    private final Set<String> failures = new HashSet<>();
    private PhotoSlideView image;
    private LinearLayout clockRow;
    private PhotoRenderer.Frame current;
    private File file;
    private boolean active, loading, paused, menuOpened, menuShowing;
    private boolean okPressedHere;
    private boolean localFallback;
    private int consecutiveFailures;
    private android.app.Dialog menu;
    private final LatestPhotoRequest<File> requests = new LatestPhotoRequest<>();
    private int requestedFade = 200;
    private File cachedFile;
    private PhotoRenderer.Frame cachedFrame;
    private PhotoAsset cachedAsset;
    private boolean prefetching;
    private boolean memoryPressure;
    private List<File> collection;
    private int width, height;
    private final java.util.Random random = new java.util.Random();
    private final Runnable advance = () -> next(getIntent().getBooleanExtra("screensaver", false) ? 2 : 1, true);
    private final Runnable longPress = () -> { menuOpened = true; showMenu(); };
    private final Runnable prefetch = this::prefetchNext;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UsbCacheAccess.initialize(this);
        width = getResources().getDisplayMetrics().widthPixels;
        height = getResources().getDisplayMetrics().heightPixels;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF080C11);
        image = new PhotoSlideView(this);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(32), dp(48), dp(32), dp(24));
        clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.BOTTOM); clockRow.setBaselineAligned(false);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextSize(54); clock.setTextColor(0xFFFFFFFF); clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        TextClock seconds = new TextClock(this);
        seconds.setFormat12Hour(":ss"); seconds.setFormat24Hour(":ss");
        seconds.setTextSize(27); seconds.setTextColor(0xDDFFFFFF); seconds.setIncludeFontPadding(false);
        seconds.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        LinearLayout.LayoutParams secondsParams = new LinearLayout.LayoutParams(-2, -2);
        secondsParams.gravity = Gravity.BOTTOM; secondsParams.leftMargin = dp(2);
        clock.setTypeface(UiFont.get(this, R.string.font_light));
        seconds.setTypeface(UiFont.get(this, R.string.font_light));
        clockRow.addView(clock); clockRow.addView(seconds, secondsParams);
        ClockAlignment.align(clock, seconds);
        ClockAlignment.stabilize(clock, seconds, null);
        details.addView(clockRow);
        root.addView(details, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT));
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // No KEEP_SCREEN_ON: the owner's TV sleep and power settings retain control.
    }

    @Override protected void onResume() {
        super.onResume(); active = true; memoryPressure = false;
        next(0); updateInfo();
    }

    private List<File> available() {
        if (collection != null) return collection;
        List<File> files = localFallback ? PhotoPreferences.localFiles(this) : PhotoPreferences.albumFiles(this);
        files.removeIf(f -> failures.contains(f.getAbsolutePath()));
        if (files.isEmpty()) {
            files = PhotoPreferences.localFiles(this);
            files.removeIf(f -> failures.contains(f.getAbsolutePath()));
        }
        collection = files;
        return collection;
    }

    private void next(int step) { next(step, false); }

    private void next(int step, boolean automatic) {
        handler.removeCallbacks(advance);
        handler.removeCallbacks(prefetch);
        if (!active) return;
        List<File> files = available();
        if (files.isEmpty()) { Toast.makeText(this, R.string.album_empty, Toast.LENGTH_LONG).show(); finish(); return; }
        File base = requests.wanted() != null ? requests.wanted() : file;
        if (base == null && getIntent().getBooleanExtra("screensaver", false)) base = files.get(random.nextInt(files.size()));
        if (base == null) {
            String initial = getIntent().getStringExtra("start_image");
            if (initial != null) for (File candidate : files) if (candidate.getAbsolutePath().equals(initial)) { base = candidate; break; }
        }
        int previous = base == null ? -1 : files.indexOf(base);
        int index;
        if (step == 2 && files.size() > 1) {
            index = random.nextInt(files.size() - 1);
            if (previous >= 0 && index >= previous) index++;
        } else index = previous < 0 ? 0 : Math.floorMod(previous + step, files.size());
        requests.request(files.get(index)); requestedFade = automatic ? 420 : 200;
        decodeRequested();
    }

    private void decodeRequested() {
        File target = requests.start();
        if (target == null || !active) return;
        loading = true;
        if (target.equals(cachedFile) && cachedFrame != null) {
            PhotoRenderer.Frame hit = cachedFrame; PhotoAsset asset = cachedAsset;
            cachedFile = null; cachedFrame = null; cachedAsset = null;
            receive(target, hit, asset, null); return;
        }
        decoder.execute(() -> {
            PhotoAsset asset = null; PhotoRenderer.Frame decoded = null; Drawable animation = null;
            try {
                asset = PhotoAsset.resolve(target, new File(getCacheDir(), "live_photos"));
                decoded = PhotoRenderer.album(asset.poster,target,width,height,new File(getCacheDir(),"album_previews"));
                if (decoded != null && asset.animated) animation = PhotoSlideView.animated(asset.poster, width, height);
            } catch (Exception | OutOfMemoryError failure) { }
            final PhotoRenderer.Frame frame = decoded; final PhotoAsset media = asset; final Drawable motion = animation;
            handler.post(() -> receive(target, frame, media, motion));
        });
    }

    private void receive(File target, PhotoRenderer.Frame frame, PhotoAsset asset, Drawable animation) {
        loading = false;
        boolean latest = requests.complete(target);
        if (!active || isFinishing()) { if (frame != null) frame.recycle(); return; }
        if (!latest) { if (frame != null) frame.recycle(); decodeRequested(); return; }
        if (frame == null) {
            failures.add(target.getAbsolutePath());
            if (collection != null) collection.remove(target);
            if (++consecutiveFailures >= 8) {
                if (localFallback) { exitAlbum(); return; }
                localFallback = true; collection = null; consecutiveFailures = 0;
            }
            handler.postDelayed(() -> next(1), 100); return;
        }
        consecutiveFailures = 0;
        PhotoRenderer.Frame old = current; current = frame; file = target;
        image.show(frame, asset, animation, width, height, requestedFade);
        image.pause(paused || menuShowing);
        if (old != null && old != current) new Handler(Looper.getMainLooper()).postDelayed(old::recycle, 500);
        updateInfo(); schedule();
        // Input takes precedence over speculative USB work, especially while browsing quickly.
        handler.postDelayed(prefetch, 250);
    }

    /** At most one adjacent still is retained; animated assets are resolved only when requested. */
    private void prefetchNext() {
        if (!active || memoryPressure || paused || menuShowing || loading || prefetching || cachedFrame != null) return;
        android.app.ActivityManager memory = getSystemService(android.app.ActivityManager.class);
        android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
        if (memory != null) { memory.getMemoryInfo(info); if (info.lowMemory) return; }
        List<File> files = available(); int at = files.indexOf(file);
        if (files.size() < 2 || at < 0) return;
        File next = files.get((at + 1) % files.size());
        String name = next.getName().toLowerCase(java.util.Locale.ROOT);
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) return;
        prefetching = true;
        decoder.execute(() -> {
            PhotoRenderer.Frame result = null; PhotoAsset asset = null;
            try {
                asset = PhotoAsset.resolve(next, new File(getCacheDir(), "live_photos"));
                if (asset.video == null && !asset.animated) result = PhotoRenderer.album(asset.poster,next,width,height,new File(getCacheDir(),"album_previews"));
            } catch (Exception | OutOfMemoryError ignored) { }
            PhotoRenderer.Frame frame = result; PhotoAsset media = asset;
            handler.post(() -> {
                prefetching = false;
                if (!active || memoryPressure || loading || cachedFrame != null || frame == null) { if (frame != null) frame.recycle(); return; }
                cachedFrame = frame; cachedFile = next; cachedAsset = media;
            });
        });
    }

    private void schedule() {
        handler.removeCallbacks(advance);
        if (active && !paused && !menuShowing) handler.postDelayed(advance, PhotoPreferences.intervalSeconds(this) * 1000L);
    }

    private void updateInfo() {
        clockRow.setVisibility(PhotoPreferences.prefs(this).getBoolean(PhotoPreferences.CLOCK, true) ? View.VISIBLE : View.GONE);
    }

    private void togglePause() {
        paused = !paused;
        image.pause(paused);
        schedule();
    }

    private void showMenu() {
        if (menuShowing || !active || loading) return;
        menuShowing = true; handler.removeCallbacks(advance); image.pause(true);
        android.app.Dialog dialog = GlassUi.menu(this, current == null ? null : current.bitmap, getString(R.string.album_menu),
                new String[]{getString(R.string.album_next), getString(R.string.photo_skip), getString(R.string.album_exit)},
                new int[]{android.R.drawable.ic_media_next, android.R.drawable.ic_menu_close_clear_cancel, R.drawable.ic_home_settings}, 0,
                which -> {
                    if (which == 2) { exitAlbum(); return; }
                    if (which == 1 && file != null) {
                        if (available().size() <= 1) { Toast.makeText(this, R.string.photo_last, Toast.LENGTH_SHORT).show(); return; }
                        PhotoPreferences.skip(this, file);
                        collection = null;
                    }
                    next(1);
                });
        menu = dialog;
        dialog.setOnDismissListener(d -> { menu = null; menuShowing = false; image.pause(paused); schedule(); });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean ok = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        if (ok) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                okPressedHere = true; menuOpened = false; handler.postDelayed(longPress, 600);
            } else if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress() && !menuOpened) {
                handler.removeCallbacks(longPress); menuOpened = true; showMenu();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPress);
                if (okPressedHere && !menuOpened) togglePause();
                okPressedHere = false;
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) showMenu();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_UP) next(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
        boolean exit = key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE || key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (exit) { if (event.getAction() == KeyEvent.ACTION_UP) exitAlbum(); return true; }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (getIntent().getBooleanExtra("screensaver", false)) exitAlbum(); else togglePause();
        }
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE || event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            if (getIntent().getBooleanExtra("screensaver", false)) { exitAlbum(); return true; }
            if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                float direction = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (direction != 0) next(direction > 0 ? -1 : 1);
                return true;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override protected void onPause() {
        active = false; requests.close(); handler.removeCallbacks(advance); handler.removeCallbacks(longPress);
        handler.removeCallbacks(prefetch); image.stopMotion();
        if (cachedFrame != null) { cachedFrame.recycle(); cachedFrame = null; cachedFile = null; cachedAsset = null; }
        rememberWallpaper();
        if (menu != null) menu.dismiss();
        super.onPause(); finish();
    }
    @Override protected void onDestroy() {
        decoder.shutdownNow(); image.clear();
        if (current != null) { current.recycle(); current = null; }
        super.onDestroy();
    }
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND) {
            memoryPressure = true;
            handler.removeCallbacks(prefetch);
            if (cachedFrame != null) { cachedFrame.recycle(); cachedFrame = null; cachedFile = null; cachedAsset = null; }
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void exitAlbum() {
        rememberWallpaper();
        startActivity(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .putExtra("album_return", true).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish(); Motion.enter(this);
    }
    private void rememberWallpaper() {
        if (file != null && current != null) getSharedPreferences("wallpaper_rotation", MODE_PRIVATE).edit()
                .putString("image", file.getName()).putLong("changed", System.currentTimeMillis()).apply();
    }
}
