package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherIconsTest {
    @Test public void clearAndPartlyCloudyRespectDayAndNight() {
        assertEquals(WeatherCodes.Icon.SUN, WeatherCodes.icon(0, true));
        assertEquals(WeatherCodes.Icon.MOON, WeatherCodes.icon(0, false));
        assertEquals(WeatherCodes.Icon.PARTLY_CLOUDY, WeatherCodes.icon(1, true));
        assertEquals(WeatherCodes.Icon.PARTLY_CLOUDY, WeatherCodes.icon(2, true));
        assertEquals(WeatherCodes.Icon.CLOUDY_NIGHT, WeatherCodes.icon(2, false));
    }
    @Test public void overcastAndFogHaveDistinctIcons() {
        assertEquals(WeatherCodes.Icon.CLOUD, WeatherCodes.icon(3, true));
        assertEquals(WeatherCodes.Icon.CLOUD, WeatherCodes.icon(3, false));
        assertEquals(WeatherCodes.Icon.FOG, WeatherCodes.icon(45, true));
        assertEquals(WeatherCodes.Icon.FOG, WeatherCodes.icon(48, false));
    }
    @Test public void drizzleFreezingRainRainAndShowersUseRainIcon() {
        for (int code : new int[]{51,53,55,56,57,61,63,65,66,67,80,81,82}) {
            assertEquals("code " + code, WeatherCodes.Icon.RAIN, WeatherCodes.icon(code, true));
        }
    }
    @Test public void snowAndSnowShowersUseSnowIcon() {
        for (int code : new int[]{71,73,75,77,85,86}) assertEquals(WeatherCodes.Icon.SNOW, WeatherCodes.icon(code, false));
    }
    @Test public void thunderAndHailRemainSeparateFromOrdinaryRain() {
        for (int code : new int[]{95,96,99}) assertEquals(WeatherCodes.Icon.THUNDER, WeatherCodes.icon(code, true));
    }
    @Test public void unknownCodesDoNotInventClearWeather() {
        for (int code : new int[]{-1,4,1000}) assertEquals(WeatherCodes.Icon.UNKNOWN, WeatherCodes.icon(code, true));
    }
}
