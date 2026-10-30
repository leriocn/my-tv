package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.LruCache;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Bundled model finds positions on this TV; no face identities or persisted templates. Called on the wallpaper worker. */
final class PhotoFaces {
    private static final LruCache<String,float[]> CACHE=new LruCache<>(32);
    private static final Semaphore SLOT=new Semaphore(1);
    private static FaceDetector detector;
    private PhotoFaces() { }
    static void trim() {
        if (!SLOT.tryAcquire()) return;
        try { if (detector != null) { detector.close(); detector = null; } synchronized(CACHE) { CACHE.evictAll(); } }
        finally { SLOT.release(); }
    }
    static float[] bounds(File file,Bitmap source) {
        // LIVP cache filenames already encode the source path/size/mtime; LRU touches must not invalidate faces.
        boolean extracted = file.getName().matches("[a-f0-9]{64}\\.(jpg|heic)");
        String key=file.getAbsolutePath()+':'+file.length()+(extracted ? "" : ":"+file.lastModified());
        synchronized(CACHE) {float[] cached=CACHE.get(key);if(cached!=null)return cached;}
        if(!SLOT.tryAcquire())return new float[0];
        Bitmap preview=null;boolean submitted=false;
        try {
            float ratio=Math.min(1f,720f/Math.max(source.getWidth(),source.getHeight()));
            int width=Math.max(2,Math.round(source.getWidth()*ratio));
            int height=Math.max(2,Math.round(source.getHeight()*ratio));
            preview=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            new Canvas(preview).drawBitmap(source,null,new Rect(0,0,width,height),new Paint(Paint.FILTER_BITMAP_FLAG));
            if(detector==null)detector=FaceDetection.getClient(new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).setMinFaceSize(.04f).build());
            Bitmap owned=preview;
            Task<List<Face>> task=detector.process(InputImage.fromBitmap(owned,0));
            // Even after timeout/cancellation, native processing owns its preview until it really completes.
            FaceDetector used = detector;
            task.addOnCompleteListener(Runnable::run,t->{
                owned.recycle();
                // Home is static. Keep tiny face-coordinate results, not the native detector between photos.
                try { used.close(); } catch (RuntimeException ignored) { }
                finally { if (detector == used) detector = null; SLOT.release(); }
            });submitted=true;
            List<Face> faces=Tasks.await(task,6,TimeUnit.SECONDS);
            List<float[]> boxes=new ArrayList<>();float biggest=0;
            for(Face face:faces) {
                Rect r=face.getBoundingBox();float w=r.width(),h=r.height();
                float[] b={Math.max(0,r.left-w*.12f)/width,Math.max(0,r.top-h*.20f)/height,
                        Math.min(width,r.right+w*.12f)/width,Math.min(height,r.bottom+h*.08f)/height};
                boxes.add(b);biggest=Math.max(biggest,(b[2]-b[0])*(b[3]-b[1]));
            }
            float[] result=new float[0];
            if(!boxes.isEmpty()) {
                result=new float[]{1,1,0,0};
                for(float[] b:boxes) {
                    if((b[2]-b[0])*(b[3]-b[1])<biggest*.08f)continue;
                    result[0]=Math.min(result[0],b[0]);result[1]=Math.min(result[1],b[1]);
                    result[2]=Math.max(result[2],b[2]);result[3]=Math.max(result[3],b[3]);
                }
            }
            synchronized(CACHE){CACHE.put(key,result);}return result;
        } catch(Exception | LinkageError | OutOfMemoryError unavailable) {
            if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
            return new float[0];
        } finally {if(!submitted){if(preview!=null)preview.recycle();SLOT.release();}}
    }
}
