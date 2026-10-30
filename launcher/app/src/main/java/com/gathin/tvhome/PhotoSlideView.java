package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaPlayer;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.io.File;

/** One visible photo. Hardware video/animated image only while the album is visible; never plays sound. */
@android.annotation.SuppressLint("NewApi") // The personal Sony runs API 28; animated images are also runtime-guarded.
final class PhotoSlideView extends FrameLayout {
    private final ImageView backdrop, still;
    private final FrameLayout foreground;
    private TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private ValueAnimator drift;
    private AnimatedImageDrawable animated;
    private boolean paused;
    private int generation;
    private final Motion.Policy motion;
    private FrameLayout outgoing;
    private PhotoRenderer.Frame displayed;

    PhotoSlideView(Context context) {
        super(context);
        motion = new Motion.Policy(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        backdrop = new ImageView(context); backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(backdrop, new LayoutParams(-1, -1));
        foreground = new FrameLayout(context);
        foreground.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(16));
            }
        });
        foreground.setClipToOutline(true);
        foreground.setElevation(dp(5));
        foreground.setBackgroundColor(0x22000000);
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(0x00000000); edge.setCornerRadius(dp(16)); edge.setStroke(dp(1), 0x35FFFFFF);
        foreground.setForeground(edge);
        addView(foreground);
        still = new ImageView(context); still.setScaleType(ImageView.ScaleType.FIT_CENTER);
        foreground.addView(still, new LayoutParams(-1, -1));
    }

    static Drawable animated(File file, int width, int height) {
        if (Build.VERSION.SDK_INT < 28) return null;
        try {
            Drawable image = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (decoder, info, src) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                double scale = Math.min(1, Math.min(width / (double) w, height / (double) h));
                decoder.setTargetSize(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
            });
            return image instanceof AnimatedImageDrawable ? image : null;
        } catch (Exception | OutOfMemoryError invalid) { return null; }
    }

    void show(PhotoRenderer.Frame frame, PhotoAsset asset, Drawable animation, int width, int height) {
        show(frame, asset, animation, width, height, 200);
    }

    void show(PhotoRenderer.Frame frame, PhotoAsset asset, Drawable animation, int width, int height, int fadeMs) {
        clearOutgoing();
        int duration = motion.duration(fadeMs);
        if (displayed != null && !displayed.bitmap.isRecycled() && duration > 0) {
            outgoing = new FrameLayout(getContext());
            outgoing.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            ImageView oldBack = new ImageView(getContext()); oldBack.setScaleType(ImageView.ScaleType.FIT_XY); oldBack.setImageBitmap(displayed.bitmap);
            outgoing.addView(oldBack, new LayoutParams(-1, -1));
            ImageView oldPhoto = new ImageView(getContext()); oldPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER); oldPhoto.setImageBitmap(displayed.foreground);
            LayoutParams oldSize = new LayoutParams((LayoutParams) foreground.getLayoutParams());
            oldPhoto.setScaleX(foreground.getScaleX()); oldPhoto.setScaleY(foreground.getScaleY());
            oldPhoto.setOutlineProvider(foreground.getOutlineProvider()); oldPhoto.setClipToOutline(true);
            outgoing.addView(oldPhoto, oldSize); addView(outgoing, new LayoutParams(-1, -1));
            FrameLayout previous = outgoing;
            // Fade the two non-overlapping image textures separately; alpha on their full-screen parent
            // allocated an extra 1080p offscreen layer on this TV for every transition.
            oldBack.forceHasOverlappingRendering(false); oldPhoto.forceHasOverlappingRendering(false);
            oldBack.animate().alpha(0).setDuration(duration).start();
            oldPhoto.animate().alpha(0).setDuration(duration).setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> { if (outgoing == previous) clearOutgoing(); }).start();
        }
        stopMotion(); paused = false;
        displayed = frame;
        backdrop.setImageBitmap(frame.bitmap);
        still.setImageBitmap(frame.foreground);
        int w = frame.foreground.getWidth(), h = frame.foreground.getHeight();
        float scale = Math.min(width * .94f / w, height * .92f / h);
        LayoutParams p = new LayoutParams(Math.round(w * scale), Math.round(h * scale), Gravity.CENTER);
        foreground.setLayoutParams(p); foreground.setScaleX(1); foreground.setScaleY(1);
        if (animation instanceof AnimatedImageDrawable && Build.VERSION.SDK_INT >= 28) {
            animated = (AnimatedImageDrawable) animation;
            animated.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            still.setImageDrawable(animated); animated.start();
        } else if (asset.video != null) {
            playVideo(asset.video);
        } else if (h > w && motion.drift()) {
            drift = ValueAnimator.ofFloat(.975f, 1f);
            drift.setDuration(9000); drift.setRepeatCount(ValueAnimator.INFINITE); drift.setRepeatMode(ValueAnimator.REVERSE);
            drift.addUpdateListener(v -> {
                float s = (float) v.getAnimatedValue();
                // This is a very slow 2.5% zoom. Avoid redrawing a TV-size texture for sub-pixel changes.
                if (Math.abs(s-foreground.getScaleX()) < .0003f && s != .975f && s != 1f) return;
                foreground.setScaleX(s); foreground.setScaleY(s);
            });
            drift.start();
        }
    }

    private void playVideo(File file) {
        int token = generation;
        texture = new TextureView(getContext()); texture.setAlpha(0);
        foreground.addView(texture, new LayoutParams(-1, -1));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                if (token != generation) return;
                try {
                    MediaPlayer media = new MediaPlayer(); player = media;
                    surface = new Surface(st); media.setSurface(surface);
                    media.setVolume(0, 0); media.setLooping(true);
                    media.setOnPreparedListener(mp -> {
                        if (token != generation) return;
                        if (!paused) mp.start();
                    });
                    media.setOnInfoListener((mp, what, extra) -> {
                        if (token == generation && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && texture != null) texture.setAlpha(1);
                        return false;
                    });
                    media.setOnErrorListener((mp, what, extra) -> {
                        if (token == generation) { releaseVideo(); }
                        return true; // An unsupported phone codec leaves the original still visible.
                    });
                    media.setDataSource(file.getAbsolutePath()); media.prepareAsync();
                } catch (Exception unsupported) { releaseVideo(); }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { if (token == generation) releaseVideo(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
    }

    void pause(boolean value) {
        paused = value;
        if (drift != null) { if (value) drift.pause(); else drift.resume(); }
        if (animated != null) { if (value) animated.stop(); else animated.start(); }
        if (player != null) try { if (value) player.pause(); else player.start(); } catch (IllegalStateException ignored) { }
    }

    void stopMotion() {
        generation++;
        if (drift != null) { drift.cancel(); drift = null; }
        if (animated != null) { animated.stop(); animated = null; }
        releaseVideo();
        if (texture != null) {
            texture.setSurfaceTextureListener(null); foreground.removeView(texture); texture = null;
        }
    }
    private void releaseVideo() {
        if (texture != null) texture.setAlpha(0);
        if (player != null) { player.setOnPreparedListener(null); player.setOnInfoListener(null); player.setOnErrorListener(null); player.release(); player = null; }
        if (surface != null) { surface.release(); surface = null; }
    }
    private void clearOutgoing() {
        if (outgoing == null) return;
        FrameLayout old = outgoing; outgoing = null; old.animate().cancel();
        for (int i = 0; i < old.getChildCount(); i++) {
            ImageView view = (ImageView) old.getChildAt(i); view.animate().cancel(); view.setImageDrawable(null);
        }
        old.removeAllViews(); removeView(old);
    }
    void clear() { clearOutgoing(); displayed = null; stopMotion(); still.setImageDrawable(null); backdrop.setImageDrawable(null); }
    private int dp(float value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }
}
