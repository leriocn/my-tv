package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** Weather is fetched on demand while Home is visible, at most twice an hour. No location or account is read. */
final class HomeWeather {
    static final String CITY = "weather_city";
    static final String ENABLED = "weather_enabled";
    static final String[] CITIES = {"济南", "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "西安", "青岛"};
    private static final double[][] COORDINATES = {{36.65,117.12},{39.90,116.41},{31.23,121.47},
            {23.13,113.26},{22.54,114.06},{30.27,120.15},{30.57,104.07},{30.59,114.31},{34.34,108.94},{36.07,120.38}};
    private static final long ROTATE_MS = 10_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private final Context context;
    private final TextView view;
    private final SharedPreferences cache;
    private final ExecutorService worker = BackgroundWork.single("Weather");
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean active, closed, loading;
    private boolean failed;
    private long attempted;
    private int shownCode = Integer.MIN_VALUE;
    private boolean shownDay;
    private String shownText = "";
    private int shownIcon;
    private boolean tomorrowPage;
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!active || closed) return;
            if (enabled()) { tomorrowPage = !tomorrowPage; showCached(city(context)); }
            main.postDelayed(this, ROTATE_MS);
        }
    };

    private void showText(String text) {
        if (text.equals(shownText)) return;
        shownText = text; render();
    }

    private void render() {
        if (shownIcon == 0) view.setText(shownText);
        else {
            android.text.SpannableString styled = new android.text.SpannableString("\uFFFC  " + shownText);
            styled.setSpan(new TextIconSpan(context.getDrawable(shownIcon),
                    Math.round(16 * context.getResources().getDisplayMetrics().density)),
                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setCompoundDrawables(null, null, null, null); view.setText(styled);
        }
        view.setContentDescription(shownText + "，天气数据来自 Open-Meteo");
    }

    HomeWeather(Context context, TextView view) {
        this.context = context;
        this.view = view;
        cache = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE);
    }

    static int city(Context context) {
        return Math.max(0, Math.min(CITIES.length - 1, context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt(CITY, 0)));
    }

    private boolean enabled() {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(ENABLED, true);
    }

    void start() {
        active = true; tomorrowPage = false; update();
        main.removeCallbacks(rotate); main.postDelayed(rotate, ROTATE_MS);
    }
    void stop() { active = false; main.removeCallbacks(rotate); }
    void close() { closed = true; active = false; worker.shutdownNow(); main.removeCallbacksAndMessages(null); }

    void update() {
        if (!active || closed) return;
        if (!enabled()) { view.setVisibility(android.view.View.GONE); return; }
        view.setVisibility(android.view.View.VISIBLE);
        int city = city(context);
        long now = System.currentTimeMillis();
        long saved = cache.getLong(city + ":saved", 0);
        boolean fresh = cache.getInt(city + ":schema", 0) == 2 && cache.contains(city + ":code") && WeatherReading.fresh(saved, now);
        showCached(city);
        if (fresh || loading || (attempted > 0 && now >= attempted && now - attempted < RETRY_MS)) return;
        attempted = now;
        loading = true;
        worker.execute(() -> {
            String fetched = null;
            int weatherCode = -1;
            boolean isDay = true;
            WeatherReading.Forecast forecast = null;
            try {
                double[] point = COORDINATES[city];
                String address = "https://api.open-meteo.com/v1/forecast?latitude=" + point[0]
                        + "&longitude=" + point[1] + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=Asia%2FShanghai";
                    JSONObject data = new JSONObject(HttpText.get(address));
                    java.time.LocalDateTime observedNow = java.time.LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                    WeatherReading reading = new WeatherReading(data, observedNow);
                    weatherCode = reading.code; isDay = reading.day;
                    fetched = String.format(Locale.CHINA, "%s · 今天 %s %d°C · %d～%d°C", CITIES[city],
                            WeatherCodes.label(weatherCode), Math.round(reading.temperature), Math.round(reading.low), Math.round(reading.high));
                    try { forecast = WeatherReading.tomorrow(data, observedNow.toLocalDate()); }
                    catch (Exception missing) { /* Keep valid current weather; never invent tomorrow. */ }
            } catch (Exception ignored) {
                // Keep the last good weather with its update time. Never invent conditions.
            }
            String result = fetched;
            int resultCode = weatherCode;
            boolean resultDay = isDay;
            if (result != null) {
                SharedPreferences.Editor edit = cache.edit().putString(city + ":text", result)
                        .putInt(city + ":code", resultCode).putBoolean(city + ":day", resultDay)
                        .putInt(city + ":schema", 2).putLong(city + ":saved", System.currentTimeMillis());
                if (forecast != null) edit.putString(city + ":tomorrow_date", forecast.date.toString())
                        .putInt(city + ":tomorrow_code", forecast.code)
                        .putString(city + ":tomorrow", String.format(Locale.CHINA, "%s · 明天 %s %d～%d°C", CITIES[city],
                                WeatherCodes.label(forecast.code), Math.round(forecast.low), Math.round(forecast.high)));
                else edit.remove(city + ":tomorrow_date").remove(city + ":tomorrow").remove(city + ":tomorrow_code");
                edit.apply();
            }
            main.post(() -> {
                loading = false;
                failed = result == null;
                if (!active || closed || city != city(context)) return;
                showCached(city);
            });
        });
    }

    private void showCached(int city) {
        long now = System.currentTimeMillis(), saved = cache.getLong(city + ":saved", 0);
        String today = cache.getString(city + ":text", "");
        String tomorrow = cache.getString(city + ":tomorrow", "");
        java.time.LocalDate date = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        boolean validTomorrow = !tomorrow.isEmpty() && saved > 0 && saved <= now
                && Instant.ofEpochMilli(saved).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().equals(date)
                && date.plusDays(1).toString().equals(cache.getString(city + ":tomorrow_date", ""));
        if (!validTomorrow) tomorrowPage = false;
        String stale = !today.isEmpty() && !WeatherReading.fresh(saved, now)
                ? "（" + DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.ofEpochMilli(saved)) + " 更新）" : "";
        if (today.isEmpty()) today = CITIES[city] + (failed ? " · 天气暂不可用" : " · 天气获取中…");
        // Both pages share the longer measured width, so work/season never shifts every ten seconds.
        float widest = Math.max(view.getPaint().measureText(today + stale),
                validTomorrow ? view.getPaint().measureText(tomorrow + stale) : 0);
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.min(Math.round(320 * density), (int)Math.ceil(widest + 16 * density + view.getPaint().measureText("  ") + 2));
        android.view.ViewGroup.LayoutParams size = view.getLayoutParams();
        if (size.width != width) { size.width = width; view.setLayoutParams(size); }
        showText((tomorrowPage ? tomorrow : today) + stale);
        setIcon(tomorrowPage ? cache.getInt(city + ":tomorrow_code", -1) : cache.getInt(city + ":code", -1),
                tomorrowPage || cache.getBoolean(city + ":day", true));
    }

    private void setIcon(int code, boolean day) {
        if (shownCode == code && shownDay == day) return;
        shownCode = code; shownDay = day;
        int resource;
        switch (WeatherCodes.icon(code, day)) {
            case SUN: resource = R.drawable.ic_weather_sun; break;
            case MOON: resource = R.drawable.ic_weather_moon; break;
            case PARTLY_CLOUDY: resource = R.drawable.ic_weather_partly; break;
            case CLOUDY_NIGHT: resource = R.drawable.ic_weather_cloudy_night; break;
            case CLOUD: resource = R.drawable.ic_weather_cloud; break;
            case FOG: resource = R.drawable.ic_weather_fog; break;
            case RAIN: resource = R.drawable.ic_weather_rain; break;
            case SNOW: resource = R.drawable.ic_weather_snow; break;
            case THUNDER: resource = R.drawable.ic_weather_thunder; break;
            default: resource = R.drawable.ic_weather_unknown;
        }
        shownIcon = resource; render();
    }
}
