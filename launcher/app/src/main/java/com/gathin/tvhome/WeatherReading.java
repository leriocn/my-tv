package com.gathin.tvhome;

import org.json.JSONObject;
import java.time.*;

/** Reject stale upstream dates and invalid temperatures rather than caching misleading weather. */
final class WeatherReading {
    final double temperature, low, high;
    final int code;
    final boolean day;
    static final class Forecast {
        final LocalDate date; final int code; final double low, high;
        Forecast(LocalDate date, int code, double low, double high) {
            this.date=date; this.code=code; this.low=low; this.high=high;
        }
    }
    static Forecast tomorrow(JSONObject data, LocalDate today) throws Exception {
        JSONObject daily=data.getJSONObject("daily");
        LocalDate expected=today.plusDays(1);
        org.json.JSONArray dates=daily.getJSONArray("time");
        for(int i=0;i<Math.min(16,dates.length());i++) {
            if(!expected.toString().equals(dates.getString(i))) continue;
            double low=daily.getJSONArray("temperature_2m_min").getDouble(i);
            double high=daily.getJSONArray("temperature_2m_max").getDouble(i);
            if(!Double.isFinite(low)||!Double.isFinite(high)||low < -100||high > 70||low>high)
                throw new IllegalArgumentException("Invalid forecast range");
            return new Forecast(expected,daily.getJSONArray("weather_code").getInt(i),low,high);
        }
        throw new IllegalArgumentException("Tomorrow forecast missing");
    }
    WeatherReading(JSONObject data, LocalDateTime now) throws Exception {
        JSONObject current = data.getJSONObject("current"), daily = data.getJSONObject("daily");
        LocalDateTime observed = LocalDateTime.parse(current.getString("time"));
        long age = Duration.between(observed, now).toMinutes();
        if (age < -10 || age > 180 || !now.toLocalDate().toString().equals(daily.getJSONArray("time").getString(0)))
            throw new IllegalArgumentException("Stale weather date");
        temperature = current.getDouble("temperature_2m");
        low = daily.getJSONArray("temperature_2m_min").getDouble(0); high = daily.getJSONArray("temperature_2m_max").getDouble(0);
        for (double value : new double[]{temperature, low, high})
            if (!Double.isFinite(value) || value < -100 || value > 70) throw new IllegalArgumentException("Invalid temperature");
        if (low > high) throw new IllegalArgumentException("Inverted range");
        code = current.getInt("weather_code"); day = current.getInt("is_day") == 1;
    }
    static boolean fresh(long saved, long now) {
        if (saved <= 0 || now < saved || now - saved >= 30 * 60_000L) return false;
        ZoneId china = ZoneId.of("Asia/Shanghai");
        return Instant.ofEpochMilli(saved).atZone(china).toLocalDate().equals(Instant.ofEpochMilli(now).atZone(china).toLocalDate());
    }
}
