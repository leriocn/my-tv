package com.gathin.tvhome;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class UsbWallpaperFilesTest {
    @Test public void readsOnlyTvPicturesAndFallsThroughEmptyDrives() throws Exception {
        File first = Files.createTempDirectory("usb-empty").toFile();
        File second = Files.createTempDirectory("usb-pictures").toFile();
        File tv = new File(second, "tv");
        assertTrue(tv.mkdir());
        Files.write(new File(second, "outside.jpg").toPath(), new byte[]{1});
        Files.write(new File(tv, "notes.txt").toPath(), new byte[]{1});
        Files.write(new File(tv, "b.PNG").toPath(), new byte[]{1});
        Files.write(new File(tv, "a.jpg").toPath(), new byte[]{1});
        List<File> images = UsbWallpaperFiles.firstCollection(Arrays.asList(first, second));
        assertEquals(2, images.size());
        assertEquals("a.jpg", images.get(0).getName());
        assertEquals("b.PNG", images.get(1).getName());
    }

    @Test public void detectsChangedDriveAndRemovalWithoutWritingToIt() throws Exception {
        File root = Files.createTempDirectory("usb-remove").toFile();
        File tv = new File(root, "tv");
        assertTrue(tv.mkdir());
        File picture = new File(tv, "a.jpg");
        Files.write(picture.toPath(), new byte[]{1});
        List<File> roots = Collections.singletonList(root);
        String before = UsbWallpaperFiles.signature(UsbWallpaperFiles.firstCollection(roots));
        Files.write(picture.toPath(), new byte[]{1, 2});
        assertNotEquals(before, UsbWallpaperFiles.signature(UsbWallpaperFiles.firstCollection(roots)));
        assertTrue(picture.delete());
        assertTrue(UsbWallpaperFiles.firstCollection(roots).isEmpty());
        assertEquals("", UsbWallpaperFiles.signature(UsbWallpaperFiles.firstCollection(roots)));
    }
}
