package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** File-only USB collection rules, testable without Android or a mounted physical drive. */
final class UsbWallpaperFiles {
    static List<File> firstCollection(List<File> roots) {
        for (File root : roots) {
            try {
                List<File> pictures = WallpaperRotation.images(new File(root, "tv"));
                if (!pictures.isEmpty()) return pictures;
            } catch (SecurityException ignored) {
                // An inaccessible drive must not hide another drive or the local fallback.
            }
        }
        return new ArrayList<>();
    }

    static String signature(List<File> pictures) {
        StringBuilder result = new StringBuilder();
        for (File file : pictures) {
            result.append(file.getAbsolutePath()).append(':').append(file.length())
                    .append(':').append(file.lastModified()).append(';');
        }
        return result.toString();
    }
}
