package com.gathin.tvhome;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Validated holiday-cn annual data. Missing or malformed years never replace known-good schedules. */
final class HolidayData {
    static List<ChinaHolidays.Break> parse(String json, int year) throws Exception {
        if (json.length() > 65536) throw new IllegalArgumentException("Holiday data too large");
        JSONObject root = new JSONObject(json);
        if (root.getInt("year") != year) throw new IllegalArgumentException("Wrong year");
        JSONArray papers = root.getJSONArray("papers"); boolean official = false;
        for (int i = 0; i < papers.length(); i++) {
            URI uri = URI.create(papers.getString(i)); String host = uri.getHost();
            if ("https".equals(uri.getScheme()) && host != null && (host.equals("gov.cn") || host.endsWith(".gov.cn"))) official = true;
        }
        if (!official) throw new IllegalArgumentException("Missing official source");
        JSONArray days = root.getJSONArray("days");
        if (days.length() < 20 || days.length() > 120) throw new IllegalArgumentException("Incomplete year");
        Map<String,List<LocalDate>> off = new LinkedHashMap<>(), work = new HashMap<>(); Set<LocalDate> seen = new HashSet<>();
        StringBuilder allNames = new StringBuilder(); int restCount = 0;
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i); LocalDate date = LocalDate.parse(day.getString("date"));
            String name = day.getString("name"); Object value = day.get("isOffDay");
            if (date.getYear() != year || !seen.add(date) || !name.matches("[\\p{IsHan}、]{2,20}") || !(value instanceof Boolean))
                throw new IllegalArgumentException("Invalid holiday day");
            boolean rest = (Boolean) value;
            (rest ? off : work).computeIfAbsent(name, ignored -> new ArrayList<>()).add(date);
            if (rest) restCount++;
            allNames.append(name);
        }
        if (restCount < 20) throw new IllegalArgumentException("Missing holidays");
        for (String name : new String[]{"元旦","春节","清明","劳动","端午","中秋","国庆"})
            if (allNames.indexOf(name) < 0) throw new IllegalArgumentException("Missing " + name);
        List<ChinaHolidays.Break> result = new ArrayList<>();
        for (Map.Entry<String,List<LocalDate>> entry : off.entrySet()) {
            List<LocalDate> dates = entry.getValue(); Collections.sort(dates);
            List<LocalDate> makeup = work.getOrDefault(entry.getKey(), Collections.emptyList());
            String[] extra = makeup.stream().sorted().map(LocalDate::toString).toArray(String[]::new);
            LocalDate start = dates.get(0), end = start;
            for (int i = 1; i <= dates.size(); i++) {
                if (i < dates.size() && dates.get(i).equals(end.plusDays(1))) { end = dates.get(i); continue; }
                result.add(new ChinaHolidays.Break(entry.getKey(), start.toString(), end.toString(), extra));
                if (i < dates.size()) { start = dates.get(i); end = start; }
            }
        }
        if (!off.keySet().containsAll(work.keySet())) throw new IllegalArgumentException("Orphan makeup days");
        return Collections.unmodifiableList(result);
    }
}
