package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.graphics.Bitmap;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Remote-controlled month calendar, backed by offline lunar and verified holiday data. */
public final class HolidayActivity extends Activity {
    private LocalDate selected;
    private YearMonth month;
    private TextView detailDate, detailLunar, detailStatus, detailTerms, detailBreak, detailLuck, detailYi, detailJi, detailClash;
    private Bitmap background;
    private Dialog dayDialog;
    private LinearLayout monthContent;
    private Motion.Policy motion;
    private Button previous, next, todayButton;
    private final java.util.concurrent.ExecutorService calculations = BackgroundWork.single("CalendarDates");
    private java.util.concurrent.Future<?> monthCalculation;
    private final java.util.LinkedHashSet<YearMonth> prepared = new java.util.LinkedHashSet<>();
    private YearMonth preparing;
    private int calculationGeneration;
    private boolean pendingDayDetails;
    private final List<View> cells = new ArrayList<>();
    private final DateTimeFormatter fullDate = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE);
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); motion = new Motion.Policy(this);
        background = getIntent().getParcelableExtra("background");
        selected = state == null ? today() : LocalDate.parse(state.getString("selected", today().toString()));
        month = YearMonth.from(selected); render();
        HolidayUpdates.refresh(this, () -> { if (!isFinishing() && !isDestroyed() && dayDialog == null) render(); });
    }
    private void render() {
        prepareMonth();
        if (monthContent != null) monthContent.animate().cancel();
        int toolbarFocus = previous != null && previous.hasFocus() ? -1 : next != null && next.hasFocus() ? 1
                : todayButton != null && todayButton.hasFocus() ? 2 : 0;
        cells.clear();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(44), dp(20), dp(44), dp(24));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView title = text(month.getYear() + "年 " + month.getMonthValue() + "月", 25, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        previous = button(header, "‹", () -> shiftMonth(-1)); previous.setContentDescription("上个月");
        todayButton = button(header, "今天", () -> { selected = today(); month = YearMonth.from(selected); render(); focusSelected(); });
        next = button(header, "›", () -> shiftMonth(1)); next.setContentDescription("下个月");
        View gap = new View(this); root.addView(gap, new LinearLayout.LayoutParams(-1, dp(12)));
        LinearLayout body = new LinearLayout(this); monthContent = body; root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout calendar = new LinearLayout(this); calendar.setOrientation(LinearLayout.VERTICAL);
        body.addView(calendar, new LinearLayout.LayoutParams(0, -1, 2.8f));
        LinearLayout weekdays = new LinearLayout(this); calendar.addView(weekdays, new LinearLayout.LayoutParams(-1, dp(24)));
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            TextView day = text(names[i], 12, i > 4 ? 0xFFA3DCC0 : 0xFF91A6B8); day.setGravity(Gravity.CENTER);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, -1, 1));
        }
        List<LocalDate> dates = CalendarMonth.days(month);
        for (int row = 0; row < 6; row++) {
            LinearLayout week = new LinearLayout(this); calendar.addView(week, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int col = 0; col < 7; col++) {
                LocalDate date = dates.get(row * 7 + col); View cell = dayCell(date); cells.add(cell);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); p.setMargins(dp(3), dp(3), dp(3), dp(3));
                week.addView(cell, p);
            }
        }
        LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(18), dp(17), dp(16), dp(16));
        LinearLayout.LayoutParams sidebar = new LinearLayout.LayoutParams(0, -1, 1); sidebar.setMarginStart(dp(18));
        ScrollView detailScroll = new ScrollView(this); detailScroll.setFocusable(false);
        detailScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        detailScroll.setFillViewport(true); detailScroll.addView(details);
        body.addView(detailScroll, sidebar); detailScroll.setBackground(rounded(0x65131F2A, 0x22FFFFFF, 1));
        detailDate = addDetail(details, 21, Color.WHITE, 0);
        detailLunar = addDetail(details, 13, 0xFFDFE5E9, 8);
        detailStatus = addDetail(details, 14, 0xFFA3DCC0, 12);
        detailTerms = addDetail(details, 12, 0xFFCEDBE5, 8);
        detailBreak = addDetail(details, 11, 0xFFBFCFDA, 8);
        TextView heading = addDetail(details, 11, 0xFFAFBFCC, 16); heading.setText("传统黄历");
        detailLuck = addDetail(details, 12, 0xFFF3DFC1, 8);
        detailClash = addDetail(details, 11, 0xFFD7DDE3, 8);
        detailYi = addDetail(details, 12, 0xFFB5E8C8, 12); detailYi.setMaxLines(3); detailYi.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailJi = addDetail(details, 12, 0xFFFFC3BE, 10); detailJi.setMaxLines(3); detailJi.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout scene = backgroundScene();
        scene.addView(root, new FrameLayout.LayoutParams(-1, -1)); setContentView(scene); showDetails();
        for (Button control : new Button[]{previous, todayButton, next}) control.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { if (event.getAction() == KeyEvent.ACTION_DOWN) focusSelected(); return true; }
            return false;
        });
        if (toolbarFocus == -1) previous.requestFocus();
        else if (toolbarFocus == 1) next.requestFocus();
        else if (toolbarFocus == 2) todayButton.requestFocus();
        else focusSelected();
    }
    private View dayCell(LocalDate date) {
        boolean current = YearMonth.from(date).equals(month); ChinaHolidays.Day status = ChinaHolidays.on(date);
        FrameLayout cell = new FrameLayout(this); cell.setFocusable(true); cell.setFocusableInTouchMode(true); cell.setClickable(true);
        int fill = status.kind == ChinaHolidays.Kind.HOLIDAY ? 0x99406549
                : status.kind == ChinaHolidays.Kind.MAKEUP ? 0x996B5434 : 0x45131F2A;
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, rounded(0xAA597488, 0xFFE0F2FF, 2));
        background.addState(new int[]{android.R.attr.state_hovered}, rounded(0x88597488, 0xFF96BCD9, 1));
        background.addState(new int[]{}, rounded(fill, date.equals(today()) ? 0xFF63B7FF : 0, date.equals(today()) ? 1 : 0));
        cell.setBackground(background);
        LinearLayout label = new LinearLayout(this); label.setOrientation(LinearLayout.VERTICAL); label.setGravity(Gravity.CENTER);
        cell.addView(label, new FrameLayout.LayoutParams(-1, -1));
        TextView number = text(String.valueOf(date.getDayOfMonth()), 21, current ? Color.WHITE : 0xFF657B8B);
        number.setTypeface(null, Typeface.BOLD); number.setGravity(Gravity.CENTER); label.addView(number);
        TextView lunar = text(prepared.contains(month) ? CalendarMonth.subtitle(date) : " ", 10, current ? 0xFFB7C9D7 : 0xFF657B8B);
        lunar.setSingleLine(true); lunar.setGravity(Gravity.CENTER); label.addView(lunar);
        String badge = CalendarMonth.badge(date);
        if (!badge.isEmpty()) {
            TextView marker = text(badge, 9, status.kind == ChinaHolidays.Kind.HOLIDAY ? 0xFFA6EDC6 : 0xFFFFD28D);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            p.setMargins(dp(5), dp(3), 0, 0); cell.addView(marker, p);
        }
        cell.setContentDescription(fullDate.format(date) + "，" + (prepared.contains(month) ? HomeCalendar.lunarDate(date) : "") + "，" + status.label());
        cell.setOnFocusChangeListener((v, focused) -> { if (focused) { selected = date; showDetails(); } });
        cell.setOnClickListener(v -> {
            selected = date;
            if (!YearMonth.from(date).equals(month)) { month = YearMonth.from(date); render(); }
            else showFullDay(date);
        });
        cell.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_UP && datesIndex(date) < 7) { todayButton.requestFocus(); return true; }
            int delta = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                    : key == KeyEvent.KEYCODE_DPAD_UP ? -7 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 7 : 0;
            if (delta == 0) return false;
            LocalDate target = date.plusDays(delta);
            if (target.getYear() < 1901 || target.getYear() > 2099) return true;
            selected = target;
            if (!YearMonth.from(target).equals(month)) { int direction = target.isAfter(date) ? 1 : -1; month = YearMonth.from(target); render(); animateMonth(direction); }
            else focusSelected();
            return true;
        });
        return cell;
    }
    private int datesIndex(LocalDate date) { return CalendarMonth.days(month).indexOf(date); }
    /** Cold astronomical calculation runs off the focus/render thread; dates and controls appear immediately. */
    private void prepareMonth() {
        if (prepared.contains(month) || month.equals(preparing)) return;
        if (monthCalculation != null) monthCalculation.cancel(true);
        YearMonth wanted = month; preparing = wanted;
        int generation = ++calculationGeneration;
        monthCalculation = calculations.submit(() -> {
            for (LocalDate date : CalendarMonth.days(wanted)) {
                if (Thread.currentThread().isInterrupted()) return;
                CalendarMonth.subtitle(date); HomeCalendar.lunarDate(date); HomeCalendar.describe(date);
            }
            runOnUiThread(() -> {
                if (generation != calculationGeneration || isDestroyed() || isFinishing()) return;
                preparing = null; prepared.add(wanted);
                while (prepared.size() > 2) prepared.remove(prepared.iterator().next());
                if (wanted.equals(month)) {
                    render();
                    if (pendingDayDetails) { pendingDayDetails = false; showFullDay(selected); }
                }
            });
        });
    }
    private FrameLayout backgroundScene() {
        FrameLayout scene = new FrameLayout(this); scene.setBackgroundColor(0xFF101A24);
        if (background != null) {
            ImageView photo = new ImageView(this); photo.setScaleType(ImageView.ScaleType.FIT_XY); photo.setImageBitmap(background);
            scene.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(this); shade.setBackgroundColor(0x55000000); scene.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        return scene;
    }
    private void showFullDay(LocalDate date) {
        if (!prepared.contains(month)) { pendingDayDetails = true; return; }
        Dialog dialog = new Dialog(this); dayDialog = dialog;
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        FrameLayout scene = backgroundScene();
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(18), dp(24), dp(20)); panel.setBackground(rounded(0x65131F2A, 0x44FFFFFF, 1));
        scene.addView(panel, new FrameLayout.LayoutParams(dp(760), dp(480), Gravity.CENTER));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView title = text(fullDate.format(date) + " · " + HomeCalendar.lunarDate(date), 20, Color.WHITE);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button(heading, "×", dialog::dismiss); close.setContentDescription("关闭详情");
        close.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_panel_close, 0, 0, 0);
        ScrollView scroll = new ScrollView(this); scroll.setFocusable(true); scroll.setFillViewport(true);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content);
        Almanac almanac = new Almanac(date);
        addDetail(content, 14, ChinaHolidays.on(date).color(), 10).setText(ChinaHolidays.on(date).label()
                + " · " + detailTerms.getText());
        if (detailBreak.length() > 0) addDetail(content, 13, 0xFFE0E6EA, 10).setText(detailBreak.getText());
        addDetail(content, 12, 0xFFB1C0CD, 20).setText("传统黄历");
        addDetail(content, 16, 0xFFF3DFC1, 10).setText(almanac.luck);
        addDetail(content, 14, 0xFFDFE5E9, 8).setText(almanac.clash + "\n" + almanac.spirits);
        addDetail(content, 15, 0xFFB5E8C8, 20).setText("宜  " + almanac.yi);
        addDetail(content, 15, 0xFFFFC3BE, 16).setText("忌  " + almanac.ji);
        addDetail(content, 13, 0xFFCCD6E0, 20).setText("彭祖百忌\n" + almanac.taboo);
        close.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { if (event.getAction() == KeyEvent.ACTION_DOWN) scroll.requestFocus(); return true; }
            return false;
        });
        dialog.setContentView(scene); dialog.setOnDismissListener(d -> dayDialog = null); dialog.show();
        android.view.Window window = dialog.getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0); window.setLayout(-1, -1); close.requestFocus();
        int duration = motion.duration(180);
        if (duration > 0) { panel.setAlpha(0); panel.animate().alpha(1).setDuration(duration).start(); }
    }
    private void focusSelected() { int index = datesIndex(selected); if (index >= 0) cells.get(index).requestFocus(); }
    private void shiftMonth(int delta) {
        pendingDayDetails = false;
        LocalDate target = CalendarMonth.changeMonth(selected, delta);
        if (target.getYear() < 1901 || target.getYear() > 2099) return;
        selected = target; month = YearMonth.from(selected); render(); animateMonth(delta);
    }
    private void animateMonth(int direction) {
        int duration = motion.duration(180); if (duration == 0 || monthContent == null) return;
        monthContent.animate().cancel(); monthContent.setAlpha(.7f);
        monthContent.setTranslationX(motion.reduced ? 0 : dp(direction > 0 ? 10 : -10));
        monthContent.animate().alpha(1).translationX(0).setDuration(duration)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    @Override public void onBackPressed() { super.onBackPressed(); Motion.enter(this); }
    private void showDetails() {
        if (detailDate == null) return;
        if (!prepared.contains(month)) { detailDate.setText(fullDate.format(selected)); return; }
        detailDate.setText(fullDate.format(selected)); detailLunar.setText(HomeCalendar.lunarDate(selected));
        ChinaHolidays.Day status = ChinaHolidays.on(selected);
        detailStatus.setText(status.label()); detailStatus.setTextColor(status.color());
        String festivals = HomeCalendar.festivals(selected), description = HomeCalendar.describe(selected);
        detailTerms.setText(description.substring(description.indexOf(" · ") + 3) + (festivals.isEmpty() ? "" : "\n" + festivals));
        String arrangement = "";
        for (ChinaHolidays.Break block : ChinaHolidays.breaks(selected.getYear())) {
            if (!block.contains(selected) && !block.makeup.contains(selected)) continue;
            arrangement = block.name + "\n" + block.start.getMonthValue() + "月" + block.start.getDayOfMonth()
                    + "日—" + block.end.getMonthValue() + "月" + block.end.getDayOfMonth() + "日 · " + block.days() + "天";
            arrangement += block.makeup.isEmpty() ? "\n无调休补班" : "\n补班：";
            for (int i = 0; i < block.makeup.size(); i++) {
                LocalDate day = block.makeup.get(i);
                arrangement += (i == 0 ? "" : "、") + day.getMonthValue() + "月" + day.getDayOfMonth() + "日";
            }
        }
        detailBreak.setText(arrangement);
        detailBreak.setVisibility(arrangement.isEmpty() ? View.GONE : View.VISIBLE);
        Almanac almanac = new Almanac(selected);
        detailLuck.setText(almanac.luck); detailClash.setText(almanac.clash);
        detailYi.setText("宜  " + almanac.yi); detailJi.setText("忌  " + almanac.ji);
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN) { shiftMonth(key == KeyEvent.KEYCODE_PAGE_UP ? -1 : 1); return true; }
        return super.onKeyDown(key, event);
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        boolean arrow = label.length() == 1;
        Button view = new Button(this); view.setText(arrow ? "" : label); view.setTextSize(12);
        view.setAllCaps(false); view.setTextColor(Color.WHITE); view.setGravity(Gravity.CENTER);
        view.setPadding(dp(arrow ? 11 : 12), 0, dp(arrow ? 11 : 12), 0);
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(label.equals("‹") ? R.drawable.ic_month_previous
                : label.equals("›") ? R.drawable.ic_month_next : R.drawable.ic_calendar_today, 0, 0, 0);
        view.setCompoundDrawablePadding(dp(arrow ? 0 : 7));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, controlShape(0xBB50718A, 0xFFF0F6FF));
        states.addState(new int[]{android.R.attr.state_pressed}, controlShape(0xBB50718A, 0xFFF0F6FF));
        states.addState(new int[]{}, controlShape(0x30FFFFFF, 0x45FFFFFF));
        view.setBackground(states);
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().cancel(); int duration = motion.duration(140);
            if (duration > 0) v.animate().scaleX(focused ? 1.04f : 1).scaleY(focused ? 1.04f : 1).setDuration(duration).start();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(arrow ? 42 : 88), dp(42)); p.setMarginStart(dp(10));
        parent.addView(view, p); view.setOnClickListener(v -> action.run()); return view;
    }
    private TextView addDetail(LinearLayout parent, int size, int color, int margin) {
        TextView view = text("", size, color); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(margin); parent.addView(view, p); return view;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setIncludeFontPadding(false); return view;
    }
    private GradientDrawable rounded(int fill, int border, int width) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(7));
        if (width > 0) shape.setStroke(dp(width), border); return shape;
    }
    private GradientDrawable controlShape(int fill, int border) {
        GradientDrawable shape = rounded(fill, border, 1); shape.setCornerRadius(dp(21)); return shape;
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("selected", selected.toString()); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() {
        ++calculationGeneration; if (monthCalculation != null) monthCalculation.cancel(true); calculations.shutdownNow();
        if (dayDialog != null) dayDialog.dismiss(); super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
