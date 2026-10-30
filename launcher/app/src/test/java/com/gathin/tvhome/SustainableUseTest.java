package com.gathin.tvhome;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import static org.junit.Assert.*;

public class SustainableUseTest {
    private JSONObject annual() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/holidays-2026.json")) {
            return new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test public void annualDatasetMatchesEveryManuallyVerifiedDay() throws Exception {
        List<ChinaHolidays.Break> parsed = HolidayData.parse(annual().toString(), 2026);
        int rest = 0, makeup = 0;
        for (LocalDate d = LocalDate.of(2026,1,1); d.getYear() == 2026; d = d.plusDays(1)) {
            final LocalDate day = d;
            boolean off = parsed.stream().anyMatch(b -> b.contains(day)), work = parsed.stream().anyMatch(b -> b.makeup.contains(day));
            assertEquals(ChinaHolidays.on(d).kind == ChinaHolidays.Kind.HOLIDAY, off);
            assertEquals(ChinaHolidays.on(d).kind == ChinaHolidays.Kind.MAKEUP, work);
            if (off) rest++; if (work) makeup++;
        }
        assertEquals(33, rest); assertEquals(6, makeup);
        assertFalse(ChinaHolidays.install(2026, Collections.emptyList()));
        assertTrue(ChinaHolidays.verified(2026));
    }
    @Test public void annualDataRejectsWrongYearUnofficialSourcesAndPartialSchedules() throws Exception {
        JSONObject wrong = annual().put("year", 2027);
        assertThrows(Exception.class, () -> HolidayData.parse(wrong.toString(), 2026));
        JSONObject fake = annual().put("papers", new JSONArray().put("https://gov.cn.example.com/fake"));
        assertThrows(Exception.class, () -> HolidayData.parse(fake.toString(), 2026));
        JSONObject partial = annual(); JSONArray days = partial.getJSONArray("days");
        for (int i = days.length() - 1; i >= 0; i--) if (days.getJSONObject(i).getString("name").equals("元旦")) days.remove(i);
        assertThrows(Exception.class, () -> HolidayData.parse(partial.toString(), 2026));
    }
    @Test public void annualDataRejectsDuplicatesCrossYearAndStringBooleans() throws Exception {
        JSONObject duplicate = annual(); duplicate.getJSONArray("days").put(duplicate.getJSONArray("days").get(0));
        assertThrows(Exception.class, () -> HolidayData.parse(duplicate.toString(), 2026));
        JSONObject cross = annual(); cross.getJSONArray("days").getJSONObject(0).put("date", "2027-01-01");
        assertThrows(Exception.class, () -> HolidayData.parse(cross.toString(), 2026));
        JSONObject string = annual(); string.getJSONArray("days").getJSONObject(0).put("isOffDay", "false");
        assertThrows(Exception.class, () -> HolidayData.parse(string.toString(), 2026));
    }
    private JSONObject weather() throws Exception {
        return new JSONObject("{\"current\":{\"time\":\"2026-10-23T18:00\",\"temperature_2m\":16,\"weather_code\":0,\"is_day\":0},"
                + "\"daily\":{\"time\":[\"2026-10-23\"],\"temperature_2m_min\":[11],\"temperature_2m_max\":[20]}}");
    }
    @Test public void weatherRejectsOldOrFutureObservationsAndImpossibleRanges() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026,10,23,18,5);
        assertEquals(16, new WeatherReading(weather(), now).temperature, 0);
        assertThrows(Exception.class, () -> new WeatherReading(weather(), now.plusHours(4)));
        assertThrows(Exception.class, () -> new WeatherReading(weather(), now.minusHours(1)));
        JSONObject inverted = weather(); inverted.getJSONObject("daily").getJSONArray("temperature_2m_min").put(0,30);
        assertThrows(Exception.class, () -> new WeatherReading(inverted, now));
        JSONObject invalid = weather(); invalid.getJSONObject("daily").getJSONArray("temperature_2m_max").put(0,1000);
        assertThrows(Exception.class, () -> new WeatherReading(invalid, now));
        assertThrows(Exception.class, () -> new WeatherReading(weather(), now.plusDays(1)));
    }
    @Test public void freshWeatherExpiresAtMidnightEvenBeforeThirtyMinutes() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long saved = LocalDateTime.of(2026,10,23,23,50).atZone(zone).toInstant().toEpochMilli();
        assertTrue(WeatherReading.fresh(saved,saved+5*60000));
        assertFalse(WeatherReading.fresh(saved,saved+11*60000));
        assertFalse(WeatherReading.fresh(saved,saved-1));
    }
    @Test public void cacheTrimsOldGroupsAndOrphansButKeepsOverlappingPlayback() throws Exception {
        File cache = Files.createTempDirectory("photo-cache").toFile();
        for (int i = 0; i < 8; i++) {
            File file = new File(cache, String.valueOf(i).repeat(64)+".jpg");
            Files.write(file.toPath(),new byte[10]); file.setLastModified(1000L+i*1000);
        }
        File part = new File(cache,"a".repeat(64)+".jpg.part"); Files.write(part.toPath(), new byte[10]);
        File unrelated = new File(cache,"notes.txt"); Files.write(unrelated.toPath(),new byte[]{1});
        PhotoCache.trim(cache,"0".repeat(64),30,3);
        assertTrue(new File(cache,"0".repeat(64)+".jpg").isFile());
        assertTrue(new File(cache,"7".repeat(64)+".jpg").isFile());
        assertFalse(new File(cache,"1".repeat(64)+".jpg").exists());
        assertFalse(part.exists()); assertTrue(unrelated.isFile());
        assertEquals(3,Arrays.stream(cache.listFiles()).filter(f->f.getName().endsWith(".jpg")).count());
    }
    @Test public void concurrentLivpResolutionPublishesCompleteFilesWithoutPartialCollisions() throws Exception {
        File root=Files.createTempDirectory("photo-concurrent").toFile(), source=new File(root,"same.livp"), cache=new File(root,"cache");
        byte[] payload=new byte[200000]; new Random(4).nextBytes(payload);
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(source))) {
            for(String name:new String[]{"photo.JPG","video.MOV"}) { zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(payload);zip.closeEntry(); }
        }
        ExecutorService workers=Executors.newFixedThreadPool(6);
        try {
            List<Future<PhotoAsset>> results=new ArrayList<>();
            for(int i=0;i<12;i++)results.add(workers.submit(()->PhotoAsset.resolve(source,cache)));
            for(Future<PhotoAsset> result:results) { PhotoAsset asset=result.get(10,TimeUnit.SECONDS);
                assertArrayEquals(payload,Files.readAllBytes(asset.poster.toPath())); assertArrayEquals(payload,Files.readAllBytes(asset.video.toPath())); }
            assertEquals(2,cache.listFiles().length);
        } finally {workers.shutdownNow();}
    }
}
