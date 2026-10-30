package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks this year and next year once per day while visible. No service, alarm or user configuration. */
final class HolidayUpdates {
    private static final ExecutorService WORKER = BackgroundWork.single("Holidays");
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static boolean loaded;
    private static final java.util.List<Runnable> LISTENERS = new java.util.ArrayList<>();
    static void refresh(Context context, Runnable changed) {
        synchronized (LISTENERS) {
            if (LISTENERS.size() < 8) LISTENERS.add(changed);
            if (!BUSY.compareAndSet(false, true)) return;
        }
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            boolean updated = false;
            try {
                SharedPreferences cache = app.getSharedPreferences("holiday_years", Context.MODE_PRIVATE);
                int current = LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear();
                SharedPreferences.Editor prune = cache.edit();
                for (String key : cache.getAll().keySet()) {
                    if (!key.matches("(data|attempt):[0-9]{4}")) continue;
                    int year = Integer.parseInt(key.substring(key.indexOf(':') + 1));
                    if (year < current - 1 || year > current + 1) prune.remove(key);
                }
                prune.apply();
                if (!loaded) {
                    loaded = true;
                    for (int year = current - 1; year <= current + 1; year++) {
                        try { String json = cache.getString("data:" + year, "");
                            if (!json.isEmpty()) { ChinaHolidays.install(year, HolidayData.parse(json, year)); updated = true; }
                        } catch (Exception invalid) { /* Keep the bundled year. */ }
                    }
                }
                for (int year = current; year <= current + 1; year++) {
                    long now = System.currentTimeMillis(), attempted = cache.getLong("attempt:" + year, 0);
                    if (attempted > 0 && now >= attempted && now - attempted < 86_400_000L) continue;
                    cache.edit().putLong("attempt:" + year, now).apply();
                    for (String base : new String[]{"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/",
                            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/"}) {
                        try {
                            String json = HttpText.get(base + year + ".json");
                            java.util.List<ChinaHolidays.Break> breaks = HolidayData.parse(json, year);
                            if (!ChinaHolidays.install(year, breaks)) break;
                            if (!json.equals(cache.getString("data:" + year, ""))) {
                                cache.edit().putString("data:" + year, json).apply(); updated = true;
                            }
                            break;
                        } catch (Exception unavailable) { /* Try the mirror, then retain the last good schedule. */ }
                    }
                }
            } finally {
                java.util.List<Runnable> callbacks;
                synchronized (LISTENERS) { callbacks = new java.util.ArrayList<>(LISTENERS); LISTENERS.clear(); BUSY.set(false); }
                if (updated) {
                    Handler main = new Handler(Looper.getMainLooper());
                    for (Runnable callback : callbacks) main.post(callback);
                }
            }
        });
    }
}
