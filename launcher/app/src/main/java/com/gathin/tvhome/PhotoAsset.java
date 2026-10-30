package com.gathin.tvhome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only media resolution. LIVP entries are streamed into bounded private cache, never beside originals. */
final class PhotoAsset {
    final File poster, video;
    final boolean animated;
    private PhotoAsset(File poster, File video, boolean animated) {
        this.poster = poster; this.video = video; this.animated = animated;
    }
    static boolean supported(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|gif|livp)$");
    }
    static PhotoAsset resolve(File source, File cache) throws Exception { return resolve(source, cache, true); }
    static PhotoAsset resolveStill(File source, File cache) throws Exception { return resolve(source, cache, false); }
    private static synchronized PhotoAsset resolve(File source, File cache, boolean motion) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Photo cancelled");
        String name = source.getName().toLowerCase(Locale.ROOT);
        if (!source.isFile()) throw new IOException("Photo removed");
        if (!name.endsWith(".livp")) {
            File paired = null;
            String stem = source.getName().replaceFirst("\\.[^.]+$", "");
            for (String suffix : motion ? new String[]{".MOV", ".mov", ".mp4", ".MP4"} : new String[0]) {
                File f = new File(source.getParentFile(), stem + suffix);
                if (f.isFile() && f.length() > 0 && f.length() <= 64L * 1024 * 1024) { paired = f; break; }
            }
            return new PhotoAsset(source, paired, motion && (name.endsWith(".gif") || name.endsWith(".webp")));
        }
        if (source.length() > 96L * 1024 * 1024) throw new IOException("LIVP too large");
        if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("No photo cache");
        String identity = source.getAbsolutePath() + ":" + source.length() + ":" + source.lastModified();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (byte b : hash) key.append(String.format(Locale.ROOT, "%02x", b & 255));
        File poster = null, video = null;
        PhotoCache.trim(cache, key.toString());
        // Entry paths are never used as destination paths. Reject ambiguous/malformed archives.
        try (ZipFile zip = new ZipFile(source)) {
            if (zip.size() > 32) throw new IOException("Unexpected LIVP contents");
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            for (ZipEntry e : entries) {
                String lower = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory()) continue;
                if (poster == null && lower.matches(".*\\.(jpg|jpeg|png|heic|heif)$")) {
                    poster = extract(zip, e, new File(cache, key + (lower.matches(".*\\.(heic|heif)$") ? ".heic" : ".jpg")), 40L * 1024 * 1024);
                } else if (motion && video == null && lower.matches(".*\\.(mov|mp4)$")) {
                    video = extract(zip, e, new File(cache, key + ".mov"), 64L * 1024 * 1024);
                }
            }
        } finally { PhotoCache.trim(cache, key.toString()); }
        if (poster == null) throw new IOException("LIVP has no supported photo");
        return new PhotoAsset(poster, video, false);
    }
    private static File extract(ZipFile zip, ZipEntry entry, File destination, long limit) throws IOException {
        if (entry.getSize() <= 0 || entry.getSize() > limit) throw new IOException("Invalid LIVP entry size");
        if (destination.isFile() && destination.length() == entry.getSize()) {
            destination.setLastModified(System.currentTimeMillis());
            return destination;
        }
        File part = new File(destination.getPath() + ".part");
        boolean completed = false;
        try {
            long count = 0;
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[32 * 1024]; int n;
                while ((n = in.read(buffer)) != -1) {
                    count += n;
                    if (count > limit || Thread.currentThread().isInterrupted()) throw new IOException("LIVP extraction cancelled");
                    out.write(buffer, 0, n);
                }
            }
            if (count != entry.getSize() || !part.renameTo(destination)) throw new IOException("Incomplete LIVP entry");
            completed = true;
            return destination;
        } finally { if (!completed) part.delete(); }
    }
}
