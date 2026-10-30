# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate
# Stable entry names for the real-device, no-Activity regression runner. Keep optimisation enabled.
-keep,allowoptimization class com.gathin.tvhome.BackgroundWork { static java.util.concurrent.ExecutorService single(java.lang.String); }
-keep,allowoptimization class com.gathin.tvhome.PhotoRenderer { static *** decode(...); }
-keep,allowoptimization class com.gathin.tvhome.PhotoRenderer { static *** album(...); }
-keep,allowoptimization class com.gathin.tvhome.PhotoRenderer { static *** home(...); }
-keep,allowoptimization class com.gathin.tvhome.AlbumPreviewCache { static java.io.File file(...); }
-keep,allowoptimization class com.gathin.tvhome.UsbCacheAccess { static void initialize(...); static boolean available(...); }
-keep,allowoptimization class com.gathin.tvhome.UsbWallpapers { static java.util.List roots(...); }
-keep,allowoptimization class com.gathin.tvhome.PhotoRenderer$Frame { android.graphics.Bitmap bitmap; android.graphics.Bitmap foreground; android.graphics.Bitmap preview; void recycle(); }
-keep,allowoptimization class com.gathin.tvhome.PhotoFaces { static void trim(); }
-keep,allowoptimization class com.gathin.tvhome.CalendarMonth { static *** days(...); static java.lang.String subtitle(...); }
-keep,allowoptimization class com.gathin.tvhome.HomeCalendar { static java.lang.String lunarDate(...); }
-keep,allowoptimization class com.gathin.tvhome.Config { static *** load(...); }
-keep,allowoptimization class com.gathin.tvhome.AppAdapter$TileListener { *; }
-keep,allowoptimization class com.gathin.tvhome.AppAdapter { <init>(...); void reload(...); void clear(); void close(); int getItemCount(); android.graphics.drawable.Drawable artworkAt(int); }
-keep,allowoptimization class androidx.recyclerview.widget.RecyclerView$Adapter { public void registerAdapterDataObserver(...); }
-keep,allowoptimization class androidx.recyclerview.widget.RecyclerView$AdapterDataObserver { *; }
# On-device regression observes the real decoder, never exports controls or changes volume.
-keep,allowoptimization class com.gathin.tvhome.PoemCatalog { *; }
-keep,allowoptimization class com.gathin.tvhome.PoemCatalog$Poem { *; }
-keep,allowoptimization class com.gathin.tvhome.PoetryLibrary { *; }
-keepclassmembers class com.gathin.tvhome.PoetryActivity { android.media.MediaPlayer player; }
-keepclassmembers class com.gathin.tvhome.PoetryActivity { android.app.Dialog selection; }
