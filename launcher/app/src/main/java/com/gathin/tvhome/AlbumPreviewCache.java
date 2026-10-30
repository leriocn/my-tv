package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Display-size album copies only, independent of original naming and source modification times. */
final class AlbumPreviewCache {
    static File file(File directory, File source, int width, int height) throws Exception {
        String identity = "album2:" + source.getAbsolutePath() + ':' + source.length() + ':' + source.lastModified() + ':' + width + ':' + height;
        return TvPhotoCache.file(directory,source,"album",identity);
    }
    static File keyedFile(File directory, String identity) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No photo cache");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
        char[] hex = "0123456789abcdef".toCharArray(), name = new char[64];
        for (int i=0;i<hash.length;i++) { int b=hash[i]&255; name[i*2]=hex[b>>>4]; name[i*2+1]=hex[b&15]; }
        return new File(directory,new String(name)+".jpg");
    }
    static void trim(File current) {
        if (!TvPhotoCache.onUsb(current)) PhotoCache.trim(current.getParentFile(),current.getName().substring(0,64),48L*1024*1024,128);
    }
}
