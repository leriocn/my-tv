package com.gathin.tvhome;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Own extracted assets only. Protect current and most recent other asset while Home/album overlap. */
final class PhotoCache {
    static void trim(File directory, String current) { trim(directory, current, 256L * 1024 * 1024, 128); }
    static void trim(File directory, String current, long limit, int maxFiles) {
        File[] files = directory.listFiles(f -> f.isFile() && f.getName().matches("[a-f0-9]{64}\\.(jpg|heic|mov)(\\.part)?"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        String previous = ""; long bytes = 0; int count = 0;
        for (File file : files) {
            if (file.getName().endsWith(".part")) { file.delete(); continue; }
            String key = file.getName().substring(0, 64);
            if (previous.isEmpty() && !key.equals(current)) previous = key;
            bytes += file.length(); count++;
        }
        for (int i = files.length - 1; i >= 0 && (bytes > limit || count > maxFiles); i--) {
            File file = files[i]; String key = file.getName().substring(0, 64);
            if (file.getName().endsWith(".part") || key.equals(current) || key.equals(previous)) continue;
            long size = file.length();
            if (file.delete()) { bytes -= size; count--; }
        }
    }
}
