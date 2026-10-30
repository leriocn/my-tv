package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;

/** Offline listening. A single decoder exists only while this screen is in the foreground. */
public final class PoetryActivity extends Activity {
    private final Handler handler = new Handler();
    private final PoetrySequence sequence = new PoetrySequence();
    private PoemCatalog library;
    private int index, position, loadGeneration;
    private boolean foreground, waiting, playing, paused;
    private long waitRemaining=-1, waitDeadline;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private Bitmap background;
    private TextView title, author, verse, meaning, progress, status, toggle;
    private ScrollView poemScroll, meaningScroll;
    private Dialog selection;
    private String initialId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        background=getIntent().getParcelableExtra("background");
        initialId=state==null?getIntent().getStringExtra("poem_id"):state.getString("poem_id");
        if(initialId==null) initialId=PoetryLibrary.progress(this);
        audioManager=getSystemService(AudioManager.class);
        focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change->{
                    if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) pause();
                },handler).setWillPauseWhenDucked(true).build();
        render();
    }
    @Override protected void onResume() {
        super.onResume(); foreground=true;
        int token=++loadGeneration;
        PoetryLibrary.load(this,loaded->{
            if(!foreground || token!=loadGeneration || isDestroyed())return;
            library=loaded;
            if(library==null){status.setText("诗词库暂不可用");return;}
            index=library.index(initialId); showPoem(); play();
        });
    }
    @Override protected void onPause() {
        foreground=false; loadGeneration++; initialId=library==null?initialId:library.poems.get(index).id;
        stop(); super.onPause();
    }
    @Override protected void onDestroy() { stop(); if(selection!=null)selection.dismiss(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("poem_id",library==null?initialId:library.poems.get(index).id);super.onSaveInstanceState(out);
    }
    private TextView text(String value,int size,int color) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        view.setIncludeFontPadding(false); return view;
    }
    private void render() {
        FrameLayout scene=GlassUi.scene(this,background);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48),dp(24),dp(48),dp(24));scene.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        progress=text("诗词熏听",15,0xFFE4E8E8);header.addView(progress,new LinearLayout.LayoutParams(0,-2,1));
        control(header,"",R.drawable.ic_panel_close,"返回主页",this::finish,44);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout columns=new LinearLayout(this);
        LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1);body.topMargin=dp(14);body.bottomMargin=dp(16);root.addView(columns,body);
        LinearLayout poem=new LinearLayout(this);poem.setOrientation(LinearLayout.VERTICAL);poem.setGravity(Gravity.CENTER_HORIZONTAL);
        poem.setPadding(dp(28),dp(20),dp(28),dp(22));
        poemScroll=new ScrollView(this);poemScroll.setFillViewport(true);poemScroll.addView(poem);
        columns.addView(poemScroll,new LinearLayout.LayoutParams(0,-1,1.3f));
        poemScroll.setBackground(GlassUi.shape(this,0x3B18242D,0x27FFFFFF,22));
        title=text("正在读取诗词…",28,0xFFFFF5DE);title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.SERIF,Typeface.NORMAL);poem.addView(title,new LinearLayout.LayoutParams(-1,-2));
        author=text("",15,0xFFD6E0E6);author.setGravity(Gravity.CENTER);LinearLayout.LayoutParams credit=new LinearLayout.LayoutParams(-1,-2);credit.topMargin=dp(10);poem.addView(author,credit);
        verse=text("",27,0xFFF8F4EA);verse.setGravity(Gravity.CENTER);verse.setTypeface(Typeface.SERIF,Typeface.NORMAL);verse.setLineSpacing(dp(8),1.12f);
        LinearLayout.LayoutParams lines=new LinearLayout.LayoutParams(-1,-2);lines.topMargin=dp(24);poem.addView(verse,lines);
        LinearLayout explanation=new LinearLayout(this);explanation.setOrientation(LinearLayout.VERTICAL);explanation.setPadding(dp(26),dp(25),dp(26),dp(24));
        TextView heading=text("诗意",17,0xFFB8D7D3);explanation.addView(heading);
        meaning=text("",22,0xFFEAF0EF);meaning.setLineSpacing(dp(9),1.15f);
        LinearLayout.LayoutParams paraphrase=new LinearLayout.LayoutParams(-1,-2);paraphrase.topMargin=dp(20);explanation.addView(meaning,paraphrase);
        meaningScroll=new ScrollView(this);meaningScroll.setFillViewport(true);meaningScroll.addView(explanation);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.leftMargin=dp(20);columns.addView(meaningScroll,right);
        meaningScroll.setBackground(GlassUi.shape(this,0x3318242D,0x20FFFFFF,22));
        // The content columns can be focused and scrolled with a remote, including long poems/meanings.
        for(ScrollView scroll:new ScrollView[]{poemScroll,meaningScroll}) {
            scroll.setFocusable(true);scroll.setBackground(contentSurface());
        }
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);
        control(footer,"上一首",R.drawable.ic_poem_previous,"上一首",()->step(-1),111);
        toggle=control(footer,"暂停",R.drawable.ic_poem_pause,"暂停或继续",this::toggle,105);
        control(footer,"下一首",R.drawable.ic_poem_next,"下一首",()->step(1),111);
        status=text("",12,0xFFCED9DE);status.setMaxLines(2);status.setPadding(dp(16),0,dp(12),0);footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        control(footer,"选诗",R.drawable.ic_apps,"按年级选择诗词",this::chooseBook,92);
        control(footer,"从头开始",R.drawable.ic_poem_reset,"从一年级第一首重新开始",()->select(0),126);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(48)));
        setContentView(scene);toggle.requestFocus();
    }
    private android.graphics.drawable.StateListDrawable contentSurface() {
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},GlassUi.shape(this,0x4B18242D,0x99D9E8E2,22));
        states.addState(new int[]{},GlassUi.shape(this,0x3318242D,0x20FFFFFF,22));return states;
    }
    private TextView control(LinearLayout row,String label,int icon,String description,Runnable action,int width) {
        TextView view=GlassUi.row(this,label,icon,action);view.setTextSize(14);view.setMinHeight(0);
        view.setPadding(dp(12),0,dp(10),0);view.setCompoundDrawablePadding(dp(7));view.setContentDescription(description);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),dp(44));p.rightMargin=dp(7);row.addView(view,p);return view;
    }
    private void showPoem() {
        PoemCatalog.Poem poem=library.poems.get(index);PoetryLibrary.remember(this,poem.id);initialId=poem.id;
        progress.setText(poem.book()+"   ·   "+(index+1)+" / "+library.poems.size());
        title.setText(poem.title);author.setText(poem.credit());verse.setText(PoetryText.listening(poem.body));meaning.setText(poem.meaning);
        poemScroll.scrollTo(0,0);meaningScroll.scrollTo(0,0);
    }
    private void select(int target) {
        if(library==null)return;stop();index=target;position=0;showPoem();play();
    }
    private void step(int delta) { if(library!=null)select(PoemCatalog.step(index,delta,library.poems.size())); }
    private void toggle() { if(!paused && (playing||waiting))pause();else play(); }
    private void play() {
        if(!foreground||library==null)return;
        paused=false;
        if(waiting){scheduleWait();return;}
        if(player!=null){
            if(audioManager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                player.start();playing=true;status.setText("真人朗读 · "+library.poems.get(index).narrator);toggleLabel("暂停",false);
            }return;
        }
        long token=sequence.begin();PoemCatalog.Poem poem=library.poems.get(index);
        try {
            boolean explanation=sequence.phase()==PoetrySequence.Phase.MEANING;
            File file=explanation?library.meaningAudio(poem):library.audio(poem);
            if(file==null){playbackFailed(explanation?"未找到本首释义音频，请补充诗词U盘音频后重试。":library.notice.isEmpty()?"未找到本首音频，可插入诗词U盘后重试。":library.notice);return;}
            if(audioManager.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){sequence.cancel();status.setText("音频正在被其他应用使用");return;}
            MediaPlayer created=new MediaPlayer();player=created;playing=true;toggleLabel("暂停",false);status.setText("准备朗读…");
            created.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            created.setDataSource(file.getAbsolutePath());
            created.setOnPreparedListener(mp->{if(!foreground||!sequence.accepts(token)||player!=mp)return;
                if(position>0)mp.seekTo(position);mp.start();status.setText(explanation?"释义朗读":"真人朗读 · "+poem.narrator);});
            created.setOnCompletionListener(mp->{if(!foreground||!sequence.accepts(token)||player!=mp)return;
                releasePlayer();position=0;playing=false;waiting=true;sequence.audioCompleted();waitRemaining=-1;scheduleWait();});
            created.setOnErrorListener((mp,what,extra)->{if(sequence.accepts(token)&&player==mp)playbackFailed("本首音频无法播放，可重试或切换下一首。");return true;});
            created.prepareAsync();
        } catch(Exception unavailable){playbackFailed("音频读取失败，可重新插入U盘后重试。");}
    }
    private void scheduleWait() {
        if(audioManager.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){paused=true;status.setText("音频正在被其他应用使用");toggleLabel("继续",true);return;}
        handler.removeCallbacksAndMessages(null);long token=sequence.begin();waiting=true;playing=false;
        long delay=waitRemaining>=0?waitRemaining:sequence.delayMs();waitDeadline=SystemClock.elapsedRealtime()+delay;
        status.setText(sequence.phase()==PoetrySequence.Phase.WAIT_MEANING?"原诗已读完 · 3秒后朗读释义":"释义已读完 · 5秒后下一首");toggleLabel("暂停",false);
        handler.postDelayed(()->{if(foreground&&waiting&&sequence.accepts(token)){
            waiting=false;waitRemaining=-1;if(sequence.finishWait())step(1);else play();
        }},delay);
    }
    private void playbackFailed(String message) {
        pause();waiting=false;position=0;waitRemaining=-1;status.setText(message);toggleLabel("重试",true);
    }
    private void pause() {
        if(waiting&&!paused)waitRemaining=Math.max(0,waitDeadline-SystemClock.elapsedRealtime());
        sequence.cancel();handler.removeCallbacksAndMessages(null);
        // Release preparing players as well: no late onPrepared may start sound after Pause.
        if(player!=null){try{position=player.isPlaying()?player.getCurrentPosition():0;}catch(IllegalStateException ignored){}releasePlayer();}
        playing=false;paused=true;audioManager.abandonAudioFocusRequest(focusRequest);toggleLabel("继续",true);status.setText("已暂停");
    }
    private void toggleLabel(String label,boolean playIcon){
        toggle.setText(label);android.graphics.drawable.Drawable icon=getDrawable(playIcon?R.drawable.ic_poem_play:R.drawable.ic_poem_pause).mutate();
        icon.setTint(0xFFE4EEF5);icon.setBounds(0,0,dp(23),dp(23));toggle.setCompoundDrawablesRelative(icon,null,null,null);
    }
    private void stop() { sequence.reset();handler.removeCallbacksAndMessages(null);releasePlayer();position=0;playing=false;waiting=false;paused=false;waitRemaining=-1;
        if(audioManager!=null&&focusRequest!=null)audioManager.abandonAudioFocusRequest(focusRequest); }
    private void releasePlayer(){if(player!=null){player.reset();player.release();player=null;}}
    private void chooseBook() {
        if(library==null)return;
        pause();String[] labels=new String[10];for(int i=0;i<10;i++)labels[i]="一二三四五".charAt(i/2)+"年级"+(i%2==0?"上册":"下册");
        selection=GlassUi.menu(this,background,"按年级选诗",labels,null,(library.poems.get(index).grade-1)*2+library.poems.get(index).term-1,book->{
            java.util.List<Integer> positions=new java.util.ArrayList<>();java.util.List<String> titles=new java.util.ArrayList<>();
            for(int i=0;i<library.poems.size();i++){PoemCatalog.Poem p=library.poems.get(i);if(p.grade==book/2+1&&p.term==book%2+1){positions.add(i);titles.add(p.title+" · "+p.author);}}
            selection=GlassUi.menu(this,background,labels[book],titles.toArray(new String[0]),null,Math.max(0,positions.indexOf(index)),which->select(positions.get(which)));
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(selection==null||!selection.isShowing()){
            if(key==KeyEvent.KEYCODE_MEDIA_NEXT||key==KeyEvent.KEYCODE_MEDIA_PREVIOUS||key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())step(key==KeyEvent.KEYCODE_MEDIA_PREVIOUS||key==KeyEvent.KEYCODE_CHANNEL_DOWN?-1:1);return true;
            }
            if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())toggle();return true;}
        } return super.dispatchKeyEvent(event);
    }
    private int dp(float value){return GlassUi.dp(this,value);}
}
