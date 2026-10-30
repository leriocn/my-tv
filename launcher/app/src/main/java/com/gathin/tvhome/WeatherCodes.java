package com.gathin.tvhome;

/** WMO weather interpretation used by Open-Meteo, including an honest unknown fallback. */
final class WeatherCodes {
    enum Icon { SUN, MOON, PARTLY_CLOUDY, CLOUDY_NIGHT, CLOUD, FOG, RAIN, SNOW, THUNDER, UNKNOWN }
    static Icon icon(int code, boolean day) {
        switch (code) {
            case 0: return day ? Icon.SUN : Icon.MOON;
            case 1: case 2: return day ? Icon.PARTLY_CLOUDY : Icon.CLOUDY_NIGHT;
            case 3: return Icon.CLOUD;
            case 45: case 48: return Icon.FOG;
            case 51: case 53: case 55: case 56: case 57: case 61: case 63: case 65:
            case 66: case 67: case 80: case 81: case 82: return Icon.RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return Icon.SNOW;
            case 95: case 96: case 99: return Icon.THUNDER;
            default: return Icon.UNKNOWN;
        }
    }

    static String label(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "晴间多云";
            case 2: return "多云";
            case 3: return "阴";
            case 45: case 48: return "雾";
            case 51: case 53: case 55: return "毛毛雨";
            case 56: case 57: case 66: case 67: return "冻雨";
            case 61: case 80: return "小雨";
            case 63: case 81: return "中雨";
            case 65: case 82: return "大雨";
            case 71: case 77: case 85: return "小雪";
            case 73: return "中雪";
            case 75: case 86: return "大雪";
            case 95: return "雷雨";
            case 96: case 99: return "雷雨冰雹";
            default: return "天气未知";
        }
    }
}
