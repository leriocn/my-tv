package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Text only on Home: no player, image allocations, network or per-second work. */
final class HomePoetry {
    private final Activity activity;
    private final Handler handler = new Handler();
    private final LinearLayout panel;
    private final TextView title, author, body;
    private final View up, down;
    private PoemCatalog library;
    private int index, generation;
    private boolean active;
    interface Open { void open(String id); }
    HomePoetry(Activity activity, FrameLayout root, View up, View down, Open open) {
        this.activity=activity; this.up=up; this.down=down;
        panel=new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12),dp(7),dp(14),dp(7)); panel.setFocusable(true); panel.setClickable(true);
        panel.setId(View.generateViewId()); panel.setVisibility(View.GONE);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},GlassUi.shape(activity,0x183C5360,0x88FFFFFF,14));
        background.addState(new int[]{android.R.attr.state_hovered},GlassUi.shape(activity,0x103C5360,0x55FFFFFF,14));
        background.addState(new int[]{},GlassUi.shape(activity,Color.TRANSPARENT,0,14)); panel.setBackground(background);
        title=text(24); title.setTypeface(Typeface.SERIF,Typeface.NORMAL); panel.addView(title);
        author=text(12); author.setTextColor(0xDDEFEAE1);
        LinearLayout.LayoutParams meta = new LinearLayout.LayoutParams(-2,-2); meta.topMargin=dp(5); panel.addView(author,meta);
        body=text(19); body.setTypeface(Typeface.SERIF,Typeface.NORMAL); body.setLineSpacing(dp(3),1.08f);
        LinearLayout.LayoutParams verse = new LinearLayout.LayoutParams(-1,-2); verse.topMargin=dp(12); panel.addView(body,verse);
        FrameLayout.LayoutParams location=new FrameLayout.LayoutParams(dp(520),-2,Gravity.TOP|Gravity.START);
        location.leftMargin=dp(44); location.topMargin=dp(139); root.addView(panel,location);
        panel.setOnClickListener(v->{ if (library!=null) open.open(library.poems.get(index).id); });
        panel.setOnKeyListener((v,key,event)->{
            if (event.getAction()!=KeyEvent.ACTION_DOWN) return key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if (key==KeyEvent.KEYCODE_DPAD_UP) { up.requestFocus(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_DOWN) { down.requestFocus(); return true; }
            if (key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (library!=null) { index=PoemCatalog.step(index,key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1,library.poems.size()); show(); }
                return true;
            } return false;
        });
        panel.setOnFocusChangeListener((v,focus)->{ handler.removeCallbacks(rotate); if (!focus && active) handler.postDelayed(rotate,60_000); });
    }
    private TextView text(int size) {
        TextView text=new TextView(activity); text.setTextSize(size); text.setTextColor(0xFFF8F4EA);
        text.setIncludeFontPadding(false); text.setShadowLayer(dp(2),0,dp(1),0xBA000000); return text;
    }
    private void fit() {
        int rows=Favorites.load(activity).size()>6?2:1;
        title.setTextSize(rows==2?20:24);author.setTextSize(rows==2?11:12);
        panel.setPadding(dp(12),dp(rows==2?5:7),dp(14),dp(rows==2?5:7));
        LinearLayout.LayoutParams meta=(LinearLayout.LayoutParams)author.getLayoutParams();meta.topMargin=dp(rows==2?3:5);author.setLayoutParams(meta);
        LinearLayout.LayoutParams verse=(LinearLayout.LayoutParams)body.getLayoutParams();verse.topMargin=dp(rows==2?7:12);body.setLayoutParams(verse);
        body.setTextSize(rows==2?16:19); body.setLineSpacing(dp(rows==2?1:3),1.02f);
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)panel.getLayoutParams();
        int top=dp(rows==2?127:139);
        if(p.topMargin!=top){p.topMargin=top;panel.setLayoutParams(p);}
    }
    private final Runnable rotate=this::rotate;
    private void rotate() {
        if (active && library!=null && !panel.hasFocus()) { index=PoemCatalog.step(index,1,library.poems.size()); show(); handler.postDelayed(rotate,60_000); }
    }
    void start() {
        active=true; int token=++generation;
        if (!PoetryLibrary.visible(activity)) { panel.setVisibility(View.GONE); return; }
        PoetryLibrary.load(activity,loaded->{
            if (!active || token!=generation || activity.isDestroyed()) return;
            library=loaded; if(library==null)return;
            index=library.index(PoetryLibrary.progress(activity)); show(); panel.setVisibility(View.VISIBLE); fit();
            handler.removeCallbacks(rotate); if(!panel.hasFocus())handler.postDelayed(rotate,60_000);
        });
    }
    private void show() {
        PoemCatalog.Poem poem=library.poems.get(index);
        title.setText(poem.title); author.setText(poem.credit()); body.setText(poem.body);
        panel.setContentDescription(poem.title+"，"+poem.credit()+"，"+poem.body+"。点击进入诗词熏听");
    }
    boolean focus() { return panel.getVisibility()==View.VISIBLE && panel.requestFocus(); }
    void stop() { active=false; generation++; handler.removeCallbacksAndMessages(null); }
    private int dp(float value){return GlassUi.dp(activity,value);}
}
