package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Quiet real Android checks. PixelCopy reads our Window behind Sony's picture-off overlay. */
final class PoetryDeviceChecks {
    /** Validate updated USB assets without opening a screen, starting sound or taking audio focus. */
    static void runAudioOnly(Instrumentation test) {
        Bundle result=new Bundle();JSONObject evidence=new JSONObject();int code=0;
        try {
            long began=SystemClock.elapsedRealtime();PoemCatalog library=PoetryLibrary.read(test.getTargetContext());
            check(library!=null&&library.directory!=null&&library.poems.size()==87,"Full USB library");
            int count=0;
            for(PoemCatalog.Poem poem:library.poems)for(File audio:new File[]{library.audio(poem),library.meaningAudio(poem)}) {
                check(audio!=null,"Missing audio "+poem.id);
                MediaMetadataRetriever metadata=new MediaMetadataRetriever();MediaPlayer player=new MediaPlayer();
                try {
                    metadata.setDataSource(audio.getPath());
                    check(Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>1000,"Audio duration "+poem.id);
                    player.setDataSource(audio.getPath());player.prepare();check(player.getDuration()>1000,"Prepare "+poem.id);count++;
                } finally { player.release();metadata.release(); }
            }
            evidence.put("native_audio_metadata_and_prepare_count",count).put("elapsed_ms",SystemClock.elapsedRealtime()-began)
                .put("no_activity_opened",true).put("no_audio_started",true).put("no_audio_focus_requested",true).put("no_volume_commands",true).put("passed",true);
            File directory=new File(test.getTargetContext().getExternalFilesDir(null),"poetry-check");
            check(directory.isDirectory()||directory.mkdirs(),"Evidence directory");
            try(FileOutputStream out=new FileOutputStream(new File(directory,"audio-only-result.json"))){out.write(evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            result.putString("stream",evidence.toString());code=-1;
        } catch(Throwable failure) { result.putString("stream","FAILED: "+android.util.Log.getStackTraceString(failure)); }
        test.finish(code,result);
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static TextView text(View view,String exact){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(exact))return(TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),exact);if(found!=null)return found;}}return null;
    }
    private static boolean contains(View view,String value){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),value))return true;}return false;
    }
    private static boolean has(Instrumentation test,Activity activity,String value){boolean[] result={false};test.runOnMainSync(()->result[0]=contains(activity.getWindow().getDecorView(),value));return result[0];}
    private static void click(Instrumentation test,Activity activity,String label){test.runOnMainSync(()->{
        TextView found=text(activity.getWindow().getDecorView(),label);
        if(found==null&&activity instanceof PoetryActivity)try{
            java.lang.reflect.Field field=PoetryActivity.class.getDeclaredField("selection");field.setAccessible(true);
            android.app.Dialog dialog=(android.app.Dialog)field.get(activity);
            if(dialog!=null&&dialog.isShowing())found=text(dialog.getWindow().getDecorView(),label);
        }catch(Exception failure){throw new RuntimeException(failure);}
        check(found!=null,"Missing button "+label);check(found.performClick(),"Button did not click");});}
    private static void await(Instrumentation test,Activity activity,String label,long timeout){long until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){if(has(test,activity,label))return;SystemClock.sleep(80);}
        try{capture(test,activity,new File(test.getTargetContext().getExternalFilesDir(null),"poetry-check/failed.png"));}catch(Exception ignored){}
        throw new AssertionError("Missing state "+label+" destroyed="+activity.isDestroyed()+" finishing="+activity.isFinishing());}
    private static void capture(Instrumentation test,Activity activity,File output)throws Exception{
        Bitmap image=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);CountDownLatch ready=new CountDownLatch(1);int[] result={-1};
        test.runOnMainSync(()->PixelCopy.request(activity.getWindow(),image,status->{result[0]=status;ready.countDown();},new Handler(Looper.getMainLooper())));
        check(ready.await(5,TimeUnit.SECONDS)&&result[0]==PixelCopy.SUCCESS,"Window PixelCopy failed: "+result[0]);
        try(FileOutputStream out=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    static void run(Instrumentation test){Bundle result=new Bundle();JSONObject evidence=new JSONObject();Activity activity=null;String original=null;Boolean originalVisibility=null;int code=0;
        try{
            android.content.Context context=test.getTargetContext();File directory=new File(context.getExternalFilesDir(null),"poetry-check");check(directory.isDirectory()||directory.mkdirs(),"Evidence directory");
            originalVisibility=PoetryLibrary.visible(context);
            long began=SystemClock.elapsedRealtime();PoemCatalog library=PoetryLibrary.read(context);check(library!=null&&library.poems.size()==87,"Full USB library");check(library.directory!=null,"USB source");
            for(PoemCatalog.Poem poem:library.poems)for(File audio:new File[]{library.audio(poem),library.meaningAudio(poem)}){check(audio!=null,"Missing audio "+poem.id);MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(audio.getPath());check(Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>1000,"Duration "+poem.id);}finally{metadata.release();}}
            evidence.put("native_audio_metadata_count",174).put("metadata_ms",SystemClock.elapsedRealtime()-began);
            original=PoetryLibrary.progress(context);
            activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity home=activity;
            await(test,home,library.poems.get(library.index(original)).title,10000);capture(test,home,new File(directory,"home.png"));
            Instrumentation.ActivityMonitor opened=test.addMonitor(PoetryActivity.class.getName(),null,false);
            String homeTitle=library.poems.get(library.index(original)).title;
            test.runOnMainSync(()->{TextView poemTitle=text(home.getWindow().getDecorView(),homeTitle);check(poemTitle!=null,"Focusable Home poem");((View)poemTitle.getParent()).performClick();});
            activity=test.waitForMonitorWithTimeout(opened,5000);test.removeMonitor(opened);check(activity!=null,"Home did not open listening");Activity listening=activity;
            await(test,listening,"真人朗读",10000);
            check(listening.getIntent().getParcelableExtra("background")!=null,"Blurred Home background was not passed");
            click(test,listening,"选诗");SystemClock.sleep(250);click(test,listening,"一年级下册");SystemClock.sleep(250);click(test,listening,"小池 · 杨万里");
            await(test,listening,"真人朗读",10000);check(has(test,listening,"小池")&&has(test,listening,"泉水悄悄流淌"),"Poem and meaning columns");
            capture(test,listening,new File(directory,"listening.png"));
            click(test,listening,"下一首");await(test,listening,"画鸡",5000);click(test,listening,"上一首");await(test,listening,"小池",5000);
            click(test,listening,"暂停");await(test,listening,"已暂停",1000);click(test,listening,"继续");await(test,listening,"真人朗读",5000);
            java.lang.reflect.Field field=PoetryActivity.class.getDeclaredField("player");field.setAccessible(true);
            test.runOnMainSync(()->{try{MediaPlayer player=(MediaPlayer)field.get(listening);player.seekTo(Math.max(0,player.getDuration()-700));}catch(Exception e){throw new RuntimeException(e);}});
            await(test,listening,"3秒后朗读释义",5000);long completed=SystemClock.elapsedRealtime();
            SystemClock.sleep(2500);check(has(test,listening,"3秒后朗读释义"),"Meaning started before3seconds");await(test,listening,"释义朗读",2000);
            long gap=SystemClock.elapsedRealtime()-completed;check(gap>=2800&&gap<=4500,"3second measured gap "+gap);evidence.put("recitation_to_meaning_ms",gap);
            // Pause/resume must retain the meaning phase rather than replaying the poem.
            click(test,listening,"暂停");click(test,listening,"继续");await(test,listening,"释义朗读",5000);
            test.runOnMainSync(()->{try{MediaPlayer player=(MediaPlayer)field.get(listening);player.seekTo(Math.max(0,player.getDuration()-700));}catch(Exception e){throw new RuntimeException(e);}});
            await(test,listening,"5秒后下一首",5000);completed=SystemClock.elapsedRealtime();
            SystemClock.sleep(4500);check(has(test,listening,"小池"),"Advanced before5seconds");await(test,listening,"画鸡",2000);
            gap=SystemClock.elapsedRealtime()-completed;check(gap>=4800&&gap<=6500,"5second measured gap "+gap);evidence.put("meaning_to_next_ms",gap);
            // A queued auto-next must not survive a manual skip or pause.
            click(test,listening,"从头开始");await(test,listening,"咏鹅",4000);click(test,listening,"下一首");await(test,listening,"远看山有色",4000);
            click(test,listening,"选诗");SystemClock.sleep(250);click(test,listening,"五年级下册");SystemClock.sleep(250);click(test,listening,"乡村四月 · 翁卷");await(test,listening,"乡村四月",5000);
            await(test,listening,"真人朗读",5000);test.runOnMainSync(()->{try{MediaPlayer player=(MediaPlayer)field.get(listening);player.seekTo(player.getDuration()-500);}catch(Exception e){throw new RuntimeException(e);}});
            await(test,listening,"3秒后朗读释义",5000);click(test,listening,"暂停");SystemClock.sleep(3500);check(has(test,listening,"乡村四月")&&has(test,listening,"已暂停"),"Pause did not cancel meaning timer");
            click(test,listening,"继续");await(test,listening,"释义朗读",4500);
            test.runOnMainSync(()->{try{MediaPlayer player=(MediaPlayer)field.get(listening);player.seekTo(player.getDuration()-500);}catch(Exception e){throw new RuntimeException(e);}});
            await(test,listening,"5秒后下一首",5000);click(test,listening,"暂停");SystemClock.sleep(5500);check(has(test,listening,"乡村四月")&&has(test,listening,"已暂停"),"Pause did not cancel auto-next");
            click(test,listening,"继续");await(test,listening,"咏鹅",6500);await(test,listening,"真人朗读",5000);
            test.runOnMainSync(listening::finish);SystemClock.sleep(600);check(field.get(listening)==null,"Player retained after exit");
            activity=test.startActivitySync(new Intent(context,LauncherSettingsActivity.class).putExtra("background",(Bitmap)listening.getIntent().getParcelableExtra("background")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity settings=activity;
            int on=context.getResources().getIdentifier("on","string",context.getPackageName()),off=context.getResources().getIdentifier("off","string",context.getPackageName());
            String shown=context.getString(originalVisibility?on:off),hidden=context.getString(originalVisibility?off:on);
            click(test,settings,"主页诗词 · "+shown);check(PoetryLibrary.visible(context)!=originalVisibility,"Home poetry switch did not change");
            click(test,settings,"主页诗词 · "+hidden);check(PoetryLibrary.visible(context)==originalVisibility,"Home poetry switch did not restore");
            capture(test,settings,new File(directory,"settings.png"));test.runOnMainSync(settings::finish);
            // Restore the owner's learning position instead of leaving the test at the last book.
            PoetryLibrary.remember(context,original);
            evidence.put("home_poetry_switch",true).put("home_entry_with_background",true).put("previous_next_pause_resume",true).put("grade_selection",true).put("reset",true).put("wrap",true).put("pause_cancels_wait",true).put("player_released_on_exit",true).put("screen_power_changed",false).put("volume_changed",false).put("passed",true);
            try(FileOutputStream out=new FileOutputStream(new File(directory,"result.json"))){out.write(evidence.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            result.putString("stream",evidence.toString());code=-1;
        }catch(Throwable failure){result.putString("stream","FAILED: "+android.util.Log.getStackTraceString(failure));}
        finally{if(original!=null)PoetryLibrary.remember(test.getTargetContext(),original);if(originalVisibility!=null)test.getTargetContext().getSharedPreferences(Config.SETTINGS_NAME,0).edit().putBoolean(PoetryLibrary.SHOW_HOME,originalVisibility).apply();if(activity!=null&&!activity.isFinishing()){Activity close=activity;test.runOnMainSync(close::finish);}}
        test.finish(code,result);
    }
}
