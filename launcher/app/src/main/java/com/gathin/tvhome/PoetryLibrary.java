package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/** One small text snapshot shared by Home and listening; USB I/O is never done by focus handlers. */
final class PoetryLibrary {
    static final String SHOW_HOME = "show_home_poetry";
    private static final String PROGRESS = "poetry_progress_id";
    private static final ExecutorService reader = BackgroundWork.single("PoetryLibrary");
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile PoemCatalog snapshot;
    interface Loaded { void accept(PoemCatalog library); }
    static boolean visible(Context context) { return PhotoPreferences.prefs(context).getBoolean(SHOW_HOME,true); }
    static String progress(Context context) { return PhotoPreferences.prefs(context).getString(PROGRESS,""); }
    static void remember(Context context, String id) { PhotoPreferences.prefs(context).edit().putString(PROGRESS,id).apply(); }
    static void load(Context context, Loaded loaded) {
        Context app = context.getApplicationContext();
        reader.execute(() -> {
            PoemCatalog result = read(app); snapshot = result;
            main.post(() -> loaded.accept(result));
        });
    }
    static PoemCatalog read(Context context) {
        File directory = null; String notice = "";
        try {
            for (File volume : UsbWallpapers.roots(context)) {
                File candidate = new File(volume,"poetry");
                if (new File(candidate,"poems.json").isFile()) { directory = candidate; break; }
            }
            if (directory != null) try (InputStream input = new FileInputStream(new File(directory,"poems.json"))) {
                return PoemCatalog.parse(readText(input),directory,"");
            }
        } catch (Exception invalid) { notice = "U盘诗词文件无法读取，已使用内置文字；可在U盘 poetry/poems.json 中检查格式。"; }
        try (InputStream input = context.getAssets().open("poetry/poems.json")) {
            return PoemCatalog.parse(readText(input),directory,notice.isEmpty() && directory==null ? "插入诗词U盘后即可播放真人朗读。" : notice);
        } catch (Exception impossible) { return snapshot; }
    }
    static String readText(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count=input.read(buffer))!=-1) {
            if (output.size()+count>PoemCatalog.MAX_BYTES) throw new java.io.IOException("Library too large");
            output.write(buffer,0,count);
        }
        String text = new String(output.toByteArray(),StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
