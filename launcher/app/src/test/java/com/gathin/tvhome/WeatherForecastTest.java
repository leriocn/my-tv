package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class WeatherForecastTest {
    private JSONObject data() throws Exception {
        return new JSONObject("{\"daily\":{\"time\":[\"2026-10-23\",\"2026-10-24\"],\"weather_code\":[0,61],\"temperature_2m_min\":[11,9],\"temperature_2m_max\":[20,18]}}");
    }
    @Test public void tomorrowUsesItsOwnConditionAndRange() throws Exception {
        WeatherReading.Forecast f=WeatherReading.tomorrow(data(),LocalDate.of(2026,10,23));
        assertEquals(LocalDate.of(2026,10,24),f.date); assertEquals(61,f.code);
        assertEquals(9,f.low,0); assertEquals(18,f.high,0);
    }
    @Test public void missingDateAndInvalidRangeAreRejected() throws Exception {
        assertThrows(Exception.class,()->WeatherReading.tomorrow(data(),LocalDate.of(2026,10,24)));
        JSONObject d=data(); d.getJSONObject("daily").getJSONArray("temperature_2m_min").put(1,25);
        assertThrows(Exception.class,()->WeatherReading.tomorrow(d,LocalDate.of(2026,10,23)));
        d.getJSONObject("daily").getJSONArray("temperature_2m_min").put(1,9);
        d.getJSONObject("daily").getJSONArray("temperature_2m_max").put(1,99);
        assertThrows(Exception.class,()->WeatherReading.tomorrow(d,LocalDate.of(2026,10,23)));
    }
}
