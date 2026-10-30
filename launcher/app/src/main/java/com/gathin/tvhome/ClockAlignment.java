package com.gathin.tvhome;

import android.graphics.Rect;
import android.widget.TextView;
import android.view.View;

/** Align visible digit bottoms, rather than font line boxes (small seconds have different descent). */
final class ClockAlignment {
    /** Keep the surrounding layout fixed even when the selected font has proportional numerals. */
    static void stabilize(TextView time, TextView seconds, TextView period) {
        java.util.List<String> times = new java.util.ArrayList<>(), ticks = new java.util.ArrayList<>();
        for (int h = 0; h < 24; h++) for (int m = 0; m < 60; m++) {
            times.add((h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m);
            if (h >= 1 && h <= 12) times.add(h + ":" + (m < 10 ? "0" : "") + m);
        }
        for (int s = 0; s < 60; s++) ticks.add(":" + (s < 10 ? "0" : "") + s);
        reserve(time, times, true); reserve(seconds, ticks, false);
        if (period != null) reserve(period, java.util.Arrays.asList("凌晨", "清晨", "上午", "中午", "下午", "傍晚", "晚上"), false);
    }
    private static void reserve(TextView view, java.util.List<String> candidates, boolean matchLength) {
        view.setFontFeatureSettings("tnum");
        view.post(() -> {
            java.util.Map<Integer, Float> widths = new java.util.HashMap<>();
            for (String text : candidates) {
                int key = matchLength ? text.length() : 0;
                widths.put(key, Math.max(widths.getOrDefault(key,0f),view.getPaint().measureText(text)));
            }
            Runnable fit = () -> {
                float widest = widths.getOrDefault(matchLength ? view.length() : 0,view.getPaint().measureText(view.getText().toString()));
                int width = (int) Math.ceil(widest) + view.getCompoundPaddingLeft() + view.getCompoundPaddingRight() + 2;
                android.view.ViewGroup.LayoutParams size = view.getLayoutParams();
                if (size.width != width) { size.width = width; view.setLayoutParams(size); }
            };
            fit.run(); view.setGravity(android.view.Gravity.START | android.view.Gravity.BOTTOM);
            if (matchLength) view.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
                @Override public void onTextChanged(CharSequence s,int start,int before,int count) { if (before != count) fit.run(); }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
        });
    }

    static void align(TextView time, TextView seconds) {
        Runnable fit = () -> {
            if (time.getBaseline() < 0 || seconds.getBaseline() < 0) return;
            Rect big = new Rect(), small = new Rect();
            String main = time.getText().toString(), suffix = seconds.getText().toString();
            if (main.isEmpty() || suffix.isEmpty()) return;
            time.getPaint().getTextBounds(main, 0, main.length(), big);
            seconds.getPaint().getTextBounds(suffix, 0, suffix.length(), small);
            seconds.setTranslationY(time.getTop() + time.getBaseline() + big.bottom
                    - seconds.getTop() - seconds.getBaseline() - small.bottom);
        };
        View.OnLayoutChangeListener layout = (v, l, t, r, b, ol, ot, or, ob) -> fit.run();
        time.addOnLayoutChangeListener(layout); seconds.addOnLayoutChangeListener(layout);
        android.text.TextWatcher changed = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { seconds.post(fit); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        };
        time.addTextChangedListener(changed); seconds.addTextChangedListener(changed);
        seconds.post(fit);
    }

    /** Date is nested inside its focus target; align the second line's visible glyphs, not its text box. */
    static void alignDate(TextView time, View container, TextView date) {
        Runnable fit = () -> {
            android.text.Layout layout = date.getLayout();
            if (layout == null || layout.getLineCount() == 0 || time.getBaseline() < 0) return;
            int last = layout.getLineCount() - 1;
            String main = time.getText().toString();
            String bottom = date.getText().subSequence(layout.getLineStart(last),layout.getLineVisibleEnd(last)).toString();
            StringBuilder letters = new StringBuilder();
            for (int i=0;i<bottom.length();i++) if (Character.isLetterOrDigit(bottom.charAt(i))) letters.append(bottom.charAt(i));
            if (letters.length()>0) bottom = letters.toString(); // Emoji artwork may extend below the text's visible baseline.
            if (main.isEmpty() || bottom.isEmpty()) return;
            Rect big = new Rect(), small = new Rect();
            time.getPaint().getTextBounds(main,0,main.length(),big);
            date.getPaint().getTextBounds(bottom,0,bottom.length(),small);
            container.setTranslationY(time.getTop()+time.getBaseline()+big.bottom
                    -container.getTop()-date.getTop()-date.getTotalPaddingTop()-layout.getLineBaseline(last)-small.bottom);
        };
        View.OnLayoutChangeListener changed = (v,l,t,r,b,ol,ot,or,ob) -> fit.run();
        time.addOnLayoutChangeListener(changed); container.addOnLayoutChangeListener(changed); date.addOnLayoutChangeListener(changed);
        date.post(fit);
    }
}
