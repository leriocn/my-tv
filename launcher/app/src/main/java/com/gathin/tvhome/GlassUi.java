package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared lightweight surfaces. The already blurred Home preview is reused, never blurred per frame. */
final class GlassUi {
    static int dp(Activity activity, float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Activity a, int fill, int stroke, int radius) {
        GradientDrawable value = new GradientDrawable(); value.setColor(fill); value.setCornerRadius(dp(a, radius));
        if (stroke != 0) value.setStroke(dp(a, 1), stroke); return value;
    }
    static StateListDrawable choices(Activity a) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(a, 0xB550718A, 0xFFE0F2FF, 16));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(a, 0xB550718A, 0xFFE0F2FF, 16));
        states.addState(new int[]{android.R.attr.state_hovered}, shape(a, 0x8050718A, 0x8896BCD9, 16));
        states.addState(new int[]{}, shape(a, 0x45131F2A, 0x25FFFFFF, 16));
        return states;
    }
    static FrameLayout scene(Activity a, Bitmap background) {
        FrameLayout root = new FrameLayout(a); root.setBackgroundColor(0xFF101A24);
        if (background != null && !background.isRecycled()) {
            ImageView image = new ImageView(a); image.setScaleType(ImageView.ScaleType.FIT_XY); image.setImageBitmap(background);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(a); shade.setBackgroundColor(0x66000000);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1)); return root;
    }
    static TextView row(Activity a, String text, int icon, Runnable action) {
        TextView row = new TextView(a); row.setText(text); row.setTextColor(Color.WHITE); row.setTextSize(17);
        row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(a, 18), dp(a, 10), dp(a, 18), dp(a, 10));
        row.setMinHeight(dp(a, 50)); row.setBackground(choices(a));
        row.setFocusable(true); row.setFocusableInTouchMode(true); row.setClickable(true);
        row.setMaxLines(2); row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (icon != 0) {
            android.graphics.drawable.Drawable drawable = a.getDrawable(icon).mutate();
            drawable.setTintList(ColorStateList.valueOf(0xFFE4EEF5)); drawable.setBounds(0, 0, dp(a, 23), dp(a, 23));
            row.setCompoundDrawablesRelative(drawable, null, null, null); row.setCompoundDrawablePadding(dp(a, 14));
        }
        row.setOnClickListener(v -> action.run()); return row;
    }
    interface Choice { void choose(int index); }
    static Dialog menu(Activity a, Bitmap background, String title, String[] labels, int[] icons, int selected, Choice action) {
        Dialog dialog = new Dialog(a); dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        FrameLayout root = scene(a, background);
        LinearLayout panel = new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 18));
        panel.setBackground(shape(a, 0xD51A2733, 0x45FFFFFF, 22));
        int available = a.getResources().getDisplayMetrics().heightPixels - dp(a, 64);
        root.addView(panel, new FrameLayout.LayoutParams(dp(a, 470), Math.min(available, dp(a, 86 + Math.min(7, labels.length) * 58)), Gravity.CENTER));
        LinearLayout header = new LinearLayout(a); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(a); heading.setText(title); heading.setTextSize(22); heading.setTextColor(Color.WHITE);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(a, 46), 1)); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = row(a, "", R.drawable.ic_panel_close, dialog::dismiss); close.setContentDescription("关闭");
        close.setPadding(dp(a, 10), 0, dp(a, 10), 0); close.setMinHeight(0);
        header.addView(close, new LinearLayout.LayoutParams(dp(a, 43), dp(a, 40))); panel.addView(header);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView row = row(a, labels[i], icons != null && i < icons.length ? icons[i] : 0,
                    () -> { dialog.dismiss(); action.choose(index); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(a, 6); rows.addView(row, p);
            row.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (key == android.view.KeyEvent.KEYCODE_DPAD_UP && index == 0) { close.requestFocus(); return true; }
                return key == android.view.KeyEvent.KEYCODE_DPAD_DOWN && index == labels.length - 1;
            });
        }
        close.setOnKeyListener((v, key, event) -> {
            if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && rows.getChildCount() > 0) rows.getChildAt(Math.max(0, Math.min(selected, labels.length - 1))).requestFocus();
                return true;
            } return false;
        });
        dialog.setContentView(root); dialog.show(); dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setDimAmount(0); dialog.getWindow().setLayout(-1, -1);
        if (labels.length > 0) rows.getChildAt(Math.max(0, Math.min(selected, labels.length - 1))).requestFocus(); else close.requestFocus();
        int duration = new Motion.Policy(a).duration(180);
        if (duration > 0) { panel.setAlpha(0); panel.setTranslationY(dp(a, 8)); panel.animate().alpha(1).translationY(0).setDuration(duration).start(); }
        return dialog;
    }
}
