package com.gathin.tvhome;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** No Activity, input or media playback: checks the real Android bitmap path in a background test process. */
public final class PerformanceInstrumentation extends Instrumentation {
    private String source;
    private boolean setup;
    private boolean poetry;
    private boolean poetryAudioOnly;
    @Override public void onCreate(Bundle args) { super.onCreate(args); source = args.getString("photo"); setup = "true".equals(args.getString("setup_cache")); poetry="true".equals(args.getString("poetry")); poetryAudioOnly="true".equals(args.getString("poetry_audio_only")); start(); }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    @Override public void onStart() {
        if(poetryAudioOnly){PoetryDeviceChecks.runAudioOnly(this);return;}
        if(poetry){PoetryDeviceChecks.run(this);return;}
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        UsbCacheAccess.initialize(getTargetContext());
        if (setup) {
            getTargetContext().startActivity(new android.content.Intent().setClassName("com.gathin.tvhome","com.gathin.tvhome.CacheAccessActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline = SystemClock.elapsedRealtime()+90000;
            while (SystemClock.elapsedRealtime()<deadline) {
                for (File volume : UsbWallpapers.roots(getTargetContext())) if (UsbCacheAccess.available(volume)) {
                    Bundle result = new Bundle(); result.putString("stream","USB_CACHE_PERMISSION_SAVED"); finish(-1,result); return;
                }
                SystemClock.sleep(250);
            }
            Bundle result = new Bundle(); result.putString("stream","USB_CACHE_PERMISSION_NOT_GRANTED"); finish(0,result); return;
        }
        Bundle results = new Bundle(); JSONObject data = new JSONObject();
        PhotoRenderer.Frame album = null, home = null;
        ExecutorService worker = BackgroundWork.single("PerfPriority");
        AppAdapter[] adapter = new AppAdapter[1];
        int finishCode = 0;
        try {
            check(worker.submit(() -> android.os.Process.getThreadPriority(android.os.Process.myTid())).get(5,TimeUnit.SECONDS) == 10, "Worker priority");
            File photo = new File(source); check(photo.isFile(), "Missing test fixture");
            long begin = SystemClock.elapsedRealtime();
            album = PhotoRenderer.decode(photo,1920,1080,0,true,false);
            check(album != null && album.foreground != null, "Album decode");
            check(album.bitmap.getWidth() == 96 && album.bitmap.getHeight() == 54, "Small backdrop");
            check(album.foreground.getWidth() <= 1805 && album.foreground.getHeight() <= 994, "Bounded foreground");
            data.put("album_decode_ms",SystemClock.elapsedRealtime()-begin);
            data.put("album_backdrop_bytes",album.bitmap.getAllocationByteCount());
            data.put("album_foreground_width",album.foreground.getWidth()).put("album_foreground_height",album.foreground.getHeight());
            album.recycle(); album = null;
            File previewCache = new File(getTargetContext().getCacheDir(),"native_test_previews");
            album = PhotoRenderer.album(photo,photo,1920,1080,previewCache);
            check(album != null,"Preview cache cold decode"); album.recycle(); album = null;
            begin = SystemClock.elapsedRealtime();
            album = PhotoRenderer.album(photo,photo,1920,1080,previewCache);
            check(album != null,"Preview cache hit decode");
            data.put("album_cached_decode_ms",SystemClock.elapsedRealtime()-begin);
            album.recycle(); album = null;
            File persistent = AlbumPreviewCache.file(previewCache,photo,1920,1080);
            data.put("persistent_album_cache",persistent.getPath());
            check(persistent.isFile(),"Persistent album cache missing");
            begin = SystemClock.elapsedRealtime();
            home = PhotoRenderer.decode(photo,1920,1080,0,true,true,1);
            check(home != null && home.preview != null, "Home decode/preview");
            check(home.bitmap.getWidth() == 1920 && home.bitmap.getHeight() == 1080, "Fullscreen Home");
            check(home.preview.getConfig() != Bitmap.Config.HARDWARE && home.preview.getWidth() <= 960 && home.preview.getHeight() <= 540, "Software preview before GPU upload");
            data.put("home_decode_ms",SystemClock.elapsedRealtime()-begin);
            data.put("home_preview_bytes",home.preview.getAllocationByteCount());
            home.recycle(); home = null;
            File homeCache = new File(getTargetContext().getCacheDir(),"home_previews");
            home = PhotoRenderer.home(photo,1920,1080,0,true,1,homeCache);
            check(home != null,"Persistent Home cold decode"); home.recycle(); home = null;
            begin = SystemClock.elapsedRealtime();
            home = PhotoRenderer.home(photo,1920,1080,0,true,1,homeCache);
            check(home != null,"Persistent Home hit decode");
            data.put("home_cached_decode_ms",SystemClock.elapsedRealtime()-begin); home.recycle(); home = null;
            begin = SystemClock.elapsedRealtime();
            for (java.time.LocalDate date : CalendarMonth.days(java.time.YearMonth.of(2026,10))) {
                CalendarMonth.subtitle(date); HomeCalendar.lunarDate(date);
            }
            data.put("calendar_42_dates_ms",SystemClock.elapsedRealtime()-begin);
            begin = SystemClock.elapsedRealtime();
            for (java.time.LocalDate date : CalendarMonth.days(java.time.YearMonth.of(2026,10))) {
                CalendarMonth.subtitle(date); HomeCalendar.lunarDate(date);
            }
            data.put("calendar_revisit_42_dates_ms",SystemClock.elapsedRealtime()-begin);
            java.util.concurrent.CountDownLatch[] ready = new java.util.concurrent.CountDownLatch[1];
            long[] reloadTime = new long[1];
            runOnMainSync(() -> {
                adapter[0] = new AppAdapter(getTargetContext(),null,6,Config.load(getTargetContext()));
                adapter[0].registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                    @Override public void onItemRangeChanged(int start,int count,Object payload) { if (ready[0] != null) for (int i=0;i<count;i++) ready[0].countDown(); }
                });
                long at = SystemClock.elapsedRealtime();
                adapter[0].reload(java.util.Collections.emptyList(), getTargetContext().getPackageManager().queryIntentActivities(
                        new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER),0));
                reloadTime[0] = SystemClock.elapsedRealtime()-at;
                ready[0] = new java.util.concurrent.CountDownLatch(adapter[0].getItemCount());
            });
            check(ready[0].await(15,TimeUnit.SECONDS),"All favorite artwork callbacks");
            runOnMainSync(() -> {
                for (int i=0;i<adapter[0].getItemCount();i++) check(adapter[0].artworkAt(i) != null,"Favorite artwork missing");
            });
            data.put("favorite_count",adapter[0].getItemCount()).put("favorite_reload_main_ms",reloadTime[0]);
            data.put("passed",true).put("activity_started",false).put("frame_pacing_tested",false);
            results.putString("stream",data.toString()); finishCode = -1;
        } catch (Throwable failure) {
            results.putString("stream", "FAILED: " + android.util.Log.getStackTraceString(failure));
        } finally {
            if (adapter[0] != null) runOnMainSync(() -> { adapter[0].clear(); adapter[0].close(); });
            worker.shutdownNow(); if (album != null) album.recycle(); if (home != null) home.recycle(); PhotoFaces.trim();
        }
        finish(finishCode,results);
    }
}
