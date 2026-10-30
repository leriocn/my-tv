package com.gathin.tvhome;

import java.io.File;

/** User-owned persistent derivatives beside /tv; private bounded cache is the read-only-drive fallback. */
final class TvPhotoCache {
    static File file(File privateDirectory, File original, String kind, String identity) throws Exception {
        File hashed = AlbumPreviewCache.keyedFile(privateDirectory,identity);
        File folder = original.getParentFile(), volume = folder == null ? null : folder.getParentFile();
        if (folder != null && folder.getName().equals("tv") && volume != null
                && volume.getAbsolutePath().matches("/storage/[A-Za-z0-9-]+")) {
            File target = new File(new File(volume,".tvcache"),kind);
            try {
                String name = original.getName().replaceAll("[^\\p{L}\\p{N}._-]","_");
                if (name.length()>64) name = name.substring(0,64);
                File cached = new File(target,name+"--"+hashed.getName());
                if (cached.isFile()) return cached;
                if (UsbCacheAccess.available(volume)) return cached;
                if ((target.isDirectory() || target.mkdirs()) && target.canWrite()) {
                    if (target.getUsableSpace()>16L*1024*1024) return cached;
                }
            } catch (SecurityException ignored) { }
        }
        return hashed;
    }
    static boolean onUsb(File file) {
        File parent = file.getParentFile(), base = parent == null ? null : parent.getParentFile();
        return base != null && base.getName().equals(".tvcache");
    }
}
