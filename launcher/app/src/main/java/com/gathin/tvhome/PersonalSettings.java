package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Everyday entries using the same Home photo and surfaces as the calendar and album. */
final class PersonalSettings {
    private final Activity activity;
    private final Bitmap background;
    private int focusedEntry;
    private Dialog dialog;
    private AppLibraryDialog library;
    private boolean closing;
    PersonalSettings(Activity activity) {
        this.activity = activity; background = activity.getIntent().getParcelableExtra("background");
    }
    void render() {
        FrameLayout scene = GlassUi.scene(activity, background);
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true);
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(64), dp(28), dp(64), dp(24)); scroll.addView(panel);
        TextView title = new TextView(activity); title.setText(R.string.settings);
        title.setTextColor(Color.WHITE); title.setTextSize(28); panel.addView(title);
        android.view.View gap = new android.view.View(activity); panel.addView(gap, new LinearLayout.LayoutParams(1, dp(22)));
        row(panel, activity.getString(R.string.album_start), R.drawable.ic_picture, () -> open(new Intent(activity, AlbumActivity.class)));
        row(panel, activity.getString(R.string.photo_settings), R.drawable.ic_settings, this::photos);
        row(panel, activity.getString(R.string.favorite_state, Favorites.load(activity).size()), R.drawable.ic_apps, this::favorites);
        row(panel, activity.getString(R.string.weather_city_state, HomeWeather.CITIES[HomeWeather.city(activity)]), R.drawable.ic_weather_partly, this::city);
        row(panel, "主页诗词 · "+state(PoetryLibrary.visible(activity)), R.drawable.ic_poem_book, () -> {
            PhotoPreferences.prefs(activity).edit().putBoolean(PoetryLibrary.SHOW_HOME,!PoetryLibrary.visible(activity)).apply();
            PhotoPreferences.changed(activity);render();
        });
        row(panel, "诗词熏听", R.drawable.ic_poem_play, () -> {
            Intent intent=new Intent(activity,PoetryActivity.class);if(background!=null)intent.putExtra("background",background);open(intent);
        });
        row(panel, activity.getString(R.string.personal_system_settings), R.drawable.ic_home_settings, () -> open(SystemSettings.intent(activity, android.provider.Settings.ACTION_SETTINGS)));
        scene.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); activity.setContentView(scene);
        panel.getChildAt(Math.min(panel.getChildCount()-1, focusedEntry + 2)).requestFocus();
    }
    private void row(LinearLayout panel, String label, int icon, Runnable action) {
        int index = panel.getChildCount() - 2;
        TextView view = GlassUi.row(activity, label, icon, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54)); p.topMargin = dp(8);
        panel.addView(view, p); view.setOnFocusChangeListener((v, focused) -> { if (focused) focusedEntry = index; });
    }
    private void menu(String title, String[] labels, int[] icons, int selected, GlassUi.Choice action) {
        if (dialog != null) dialog.dismiss();
        Dialog shown = GlassUi.menu(activity, background, title, labels, icons, selected, action);
        dialog = shown; shown.setOnDismissListener(d -> { if (dialog == shown) dialog = null; });
    }
    private String state(boolean on) { return activity.getString(on ? R.string.on : R.string.off); }
    private void photos() {
        int idle = PhotoPreferences.idleMinutes(activity), speed = PhotoPreferences.intervalSeconds(activity);
        String[] labels = {activity.getString(R.string.photo_idle, idle == 0 ? activity.getString(R.string.photo_idle_off)
                : activity.getString(R.string.photo_idle_minutes, idle)), activity.getString(R.string.photo_speed, speed),
                activity.getString(R.string.photo_clock, state(PhotoPreferences.prefs(activity).getBoolean(PhotoPreferences.CLOCK, true))),
                activity.getString(R.string.photo_restore, PhotoPreferences.skippedCount(activity)), "U盘缓存权限"};
        java.util.List<java.io.File> drives = UsbWallpapers.roots(activity);
        boolean needsGrant = !drives.isEmpty() && !UsbCacheAccess.available(drives.get(0));
        if (!needsGrant) labels = java.util.Arrays.copyOf(labels,4);
        menu(activity.getString(R.string.photo_settings), labels,
                new int[]{android.R.drawable.ic_lock_idle_alarm, R.drawable.ic_swap_vert, R.drawable.ic_calendar_today, android.R.drawable.ic_menu_revert, R.drawable.ic_usb}, 0,
                which -> {
                    if (which == 0) chooseNumber(PhotoPreferences.IDLE, new int[]{0, 5, 10, 15}, idle, true);
                    else if (which == 1) chooseNumber(PhotoPreferences.INTERVAL, new int[]{15, 30, 60}, speed, false);
                    else if (which == 2) {
                        String key = PhotoPreferences.CLOCK;
                        PhotoPreferences.prefs(activity).edit().putBoolean(key, !PhotoPreferences.prefs(activity).getBoolean(key, true)).apply();
                        PhotoPreferences.changed(activity); photos();
                    } else if (which == 4) {
                        open(new Intent(activity,CacheAccessActivity.class));
                    } else {
                        PhotoPreferences.restore(activity); Toast.makeText(activity, R.string.photo_restored, Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void chooseNumber(String key, int[] values, int current, boolean idle) {
        String[] labels = new String[values.length]; int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] == 0 ? activity.getString(R.string.photo_idle_off)
                    : activity.getString(idle ? R.string.photo_idle_minutes : R.string.photo_speed_seconds, values[i]);
            if (values[i] == current) checked = i;
            if (values[i] == current) labels[i] += " · 当前";
        }
        menu(activity.getString(R.string.photo_settings), labels, null, checked, which -> {
            PhotoPreferences.prefs(activity).edit().putInt(key, values[which]).apply();
            PhotoPreferences.changed(activity); photos();
        });
    }
    private void city() {
        menu(activity.getString(R.string.weather_city_title), HomeWeather.CITIES, null, HomeWeather.city(activity), which -> {
            PhotoPreferences.prefs(activity).edit().putInt(HomeWeather.CITY, which).apply();
            PhotoPreferences.changed(activity); render();
        });
    }
    private void favorites() {
        library = new AppLibraryDialog(activity, Config.load(activity).animations, Config.load(activity).reducedMotion, true,
                pkg -> Favorites.load(activity).contains(pkg), () -> PhotoPreferences.changed(activity),
                () -> { library = null; if (!closing && !activity.isFinishing()) render(); });
        library.show();
    }
    void close() {
        closing = true;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (library != null) { library.close(); library = null; }
    }
    private void open(Intent intent) {
        try { activity.startActivity(intent); Motion.enter(activity); }
        catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show(); }
    }
    private int dp(int value) { return GlassUi.dp(activity, value); }
}
