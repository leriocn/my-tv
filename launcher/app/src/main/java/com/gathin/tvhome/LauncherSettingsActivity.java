package com.gathin.tvhome;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private PersonalSettings personalSettings;
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView wallpaperSourceChoice;
    private TextView wallpaperIntervalChoice;
    private TextView wallpaperOrderChoice;
    private TextView appSortChoice;
    private TextView weatherCityChoice, weatherEnabledChoice;
    private static final int USB_PERMISSION_REQUEST = 73;
    private static final int[] WALLPAPER_INTERVALS = {1, 5, 10, 15, 30, 60};
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override protected void onDestroy() {
        if (personalSettings != null) personalSettings.close();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getIntent().getBooleanExtra("advanced", false)) {
            personalSettings = new PersonalSettings(this);
            personalSettings.render();
            return;
        }
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton && !LastOpened.recentSort(this) ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        appSortChoice = findViewById(R.id.appSortChoice);
        appSortChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(LastOpened.SORT_RECENT, !LastOpened.recentSort(this)).apply();
            changed();
            refresh();
        });
        weatherCityChoice = findViewById(R.id.weatherCityChoice);
        weatherCityChoice.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.weather_city_title)
                .setSingleChoiceItems(HomeWeather.CITIES, HomeWeather.city(this), (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(HomeWeather.CITY, which).apply();
                    changed();
                    refresh();
                }).setNegativeButton(android.R.string.cancel, null).show());
        weatherEnabledChoice = findViewById(R.id.weatherEnabledChoice);
        weatherEnabledChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(HomeWeather.ENABLED, !choices.getBoolean(HomeWeather.ENABLED, true)).apply();
            changed();
            refresh();
        });
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperSourceChoice = findViewById(R.id.wallpaperSourceChoice);
        wallpaperSourceChoice.setOnClickListener(v -> showWallpaperSource());
        wallpaperIntervalChoice = findViewById(R.id.wallpaperIntervalChoice);
        wallpaperIntervalChoice.setOnClickListener(v -> {
            String[] labels = new String[WALLPAPER_INTERVALS.length];
            for (int i = 0; i < labels.length; i++) labels[i] = getString(R.string.wallpaper_minutes, WALLPAPER_INTERVALS[i]);
            new AlertDialog.Builder(this).setTitle(R.string.wallpaper_interval_title)
                    .setItems(labels, (dialog, index) -> {
                        choices.edit().putInt(UsbWallpapers.INTERVAL, WALLPAPER_INTERVALS[index]).apply();
                        changed();
                        refresh();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        });
        wallpaperOrderChoice = findViewById(R.id.wallpaperOrderChoice);
        wallpaperOrderChoice.setOnClickListener(v -> {
            String current = Config.load(this).wallpaperOrder;
            choices.edit().putString(UsbWallpapers.ORDER,
                    WallpaperRotation.ORDER_SHUFFLE.equals(current) ? WallpaperRotation.ORDER_SEQUENTIAL
                            : WallpaperRotation.ORDER_SHUFFLE).apply();
            changed();
            refresh();
        });
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(UsbWallpapers.ENABLED)
                    .remove(UsbWallpapers.INTERVAL).remove(UsbWallpapers.ORDER)
                    .remove(LastOpened.SORT_RECENT).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (reorderChoice.getVisibility() == View.VISIBLE) reorderChoice.requestFocus();
        else appSortChoice.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (personalSettings != null) personalSettings.render();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        if (UsbWallpapers.enabled(this)) {
            List<File> usb = UsbWallpapers.images(this);
            if (!usb.isEmpty()) {
                images.clear();
                images.addAll(usb);
            }
        }
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void showWallpaperSource() {
        new AlertDialog.Builder(this).setTitle(R.string.wallpaper_source_title)
                .setSingleChoiceItems(new String[]{getString(R.string.wallpaper_source_usb),
                        getString(R.string.wallpaper_source_local)}, UsbWallpapers.enabled(this) ? 0 : 1,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 0 && !UsbWallpapers.supported()) {
                                Toast.makeText(this, R.string.wallpaper_usb_unsupported, Toast.LENGTH_LONG).show();
                                return;
                            }
                            choices.edit().putBoolean(UsbWallpapers.ENABLED, which == 0)
                                    .remove(Config.WALLPAPER_CHOICE_KEY).apply();
                            changed();
                            refresh();
                            if (which == 0 && !UsbWallpapers.readable(this)) {
                                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, USB_PERMISSION_REQUEST);
                            }
                        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == USB_PERMISSION_REQUEST) {
            changed();
            refresh();
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.wallpaper_usb_permission, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        boolean recent = LastOpened.recentSort(this);
        weatherCityChoice.setText(getString(R.string.weather_city_state, HomeWeather.CITIES[HomeWeather.city(this)]));
        weatherEnabledChoice.setText(getString(R.string.weather_enabled_state,
                getString(choices.getBoolean(HomeWeather.ENABLED, true) ? R.string.on : R.string.off)));
        appSortChoice.setText(getString(R.string.app_sort_state,
                getString(recent ? R.string.app_sort_recent : R.string.app_sort_manual)));
        findViewById(R.id.reorderChoice).setVisibility(Config.load(this).launcherSettingsButton && !recent ? View.VISIBLE : View.GONE);
        Config current = Config.load(this);
        List<File> usb = UsbWallpapers.enabled(this) ? UsbWallpapers.images(this) : java.util.Collections.emptyList();
        String source = !UsbWallpapers.enabled(this) ? getString(R.string.wallpaper_source_local)
                : !UsbWallpapers.readable(this) ? getString(R.string.wallpaper_usb_permission)
                : usb.isEmpty() ? getString(R.string.wallpaper_usb_fallback)
                : getString(R.string.wallpaper_usb_count, usb.size());
        wallpaperSourceChoice.setText(getString(R.string.wallpaper_source_state, source));
        wallpaperIntervalChoice.setText(getString(R.string.wallpaper_interval_state, current.wallpaperRotationMinutes));
        wallpaperOrderChoice.setText(getString(R.string.wallpaper_order_state,
                getString(WallpaperRotation.ORDER_SEQUENTIAL.equals(current.wallpaperOrder)
                        ? R.string.wallpaper_order_sequential : R.string.wallpaper_order_shuffle)));
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(UsbWallpapers.ENABLED) || choices.contains(UsbWallpapers.INTERVAL)
                || choices.contains(UsbWallpapers.ORDER) || choices.contains(LastOpened.SORT_RECENT);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
