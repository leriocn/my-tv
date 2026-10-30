package com.gathin.tvhome;

import org.junit.Test;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;

public class PhotoAssetTest {
    @Test public void staticHomeDoesNotExtractVideoButAlbumStillResolvesIt() throws Exception {
        File dir = Files.createTempDirectory("static-live").toFile(), cache = new File(dir,"cache");
        File source = archive(dir,"family.livp","family.JPG","family.MOV");
        byte[] original = Files.readAllBytes(source.toPath());
        PhotoAsset still = PhotoAsset.resolveStill(source,cache);
        assertNotNull(still.poster); assertNull(still.video); assertFalse(still.animated);
        assertEquals(1,cache.listFiles().length);
        PhotoAsset live = PhotoAsset.resolve(source,cache);
        assertEquals(still.poster,live.poster); assertNotNull(live.video);
        assertArrayEquals(original,Files.readAllBytes(source.toPath()));
        assertNull(PhotoAsset.resolveStill(source,cache).video);
        assertTrue(live.video.isFile());
    }
    private File archive(File dir, String name, String... entries) throws Exception {
        File file = new File(dir, name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry)); zip.write(new byte[]{1, 2, 3, 4}); zip.closeEntry();
            }
        }
        return file;
    }
    @Test public void livpUnpacksPhotoAndVideoWithoutTouchingOriginal() throws Exception {
        File dir = Files.createTempDirectory("live-test").toFile(), cache = new File(dir, "cache");
        File source = archive(dir, "sample.livp", "photo.JPG", "photo.MOV");
        byte[] original = Files.readAllBytes(source.toPath()); long time = source.lastModified();
        PhotoAsset asset = PhotoAsset.resolve(source, cache);
        assertTrue(asset.poster.isFile()); assertTrue(asset.video.isFile()); assertFalse(asset.animated);
        assertEquals(cache.getCanonicalPath(), asset.poster.getParentFile().getCanonicalPath());
        assertArrayEquals(original, Files.readAllBytes(source.toPath())); assertEquals(time, source.lastModified());
        assertEquals(asset.poster, PhotoAsset.resolve(source, cache).poster);
    }
    @Test public void entryPathsCannotEscapePrivateCache() throws Exception {
        File dir = Files.createTempDirectory("live-path").toFile(), cache = new File(dir, "cache");
        File source = archive(dir, "sample.livp", "../../escaped.jpg", "/absolute.MOV");
        PhotoAsset asset = PhotoAsset.resolve(source, cache);
        assertEquals(cache.getCanonicalPath(), asset.poster.getParentFile().getCanonicalPath());
        assertFalse(new File(dir, "escaped.jpg").exists());
    }
    @Test public void unsupportedArchiveAndMissingOriginalAreRejected() throws Exception {
        File dir = Files.createTempDirectory("live-bad").toFile();
        File source = archive(dir, "sample.livp", "notes.txt", "video.MOV");
        assertThrows(IOException.class, () -> PhotoAsset.resolve(source, new File(dir, "cache")));
        assertThrows(IOException.class, () -> PhotoAsset.resolve(new File(dir, "missing.jpg"), dir));
    }
    @Test public void heicLivePhotoPosterIsRetainedWithItsCodecExtension() throws Exception {
        File dir = Files.createTempDirectory("live-heic").toFile();
        File file = archive(dir, "heic.livp", "capture.HEIC", "capture.MOV");
        PhotoAsset asset = PhotoAsset.resolve(file, new File(dir, "cache"));
        assertTrue(asset.poster.getName().endsWith(".heic")); assertNotNull(asset.video);
    }
    @Test public void archiveEntryCountIsBounded() throws Exception {
        File dir = Files.createTempDirectory("live-count").toFile();
        String[] entries = new String[33]; for (int i = 0; i < entries.length; i++) entries[i] = i + ".jpg";
        File file = archive(dir, "many.livp", entries);
        assertThrows(IOException.class, () -> PhotoAsset.resolve(file, new File(dir, "cache")));
    }
    @Test public void pairedLiveAndAnimatedImagesAreRecognized() throws Exception {
        File dir = Files.createTempDirectory("live-pair").toFile();
        File jpg = new File(dir, "family.jpg"); Files.write(jpg.toPath(), new byte[]{1});
        File mov = new File(dir, "family.MOV"); Files.write(mov.toPath(), new byte[]{2});
        assertEquals(mov, PhotoAsset.resolve(jpg, dir).video);
        File gif = new File(dir, "motion.gif"); Files.write(gif.toPath(), new byte[]{3});
        assertTrue(PhotoAsset.resolve(gif, dir).animated);
        assertTrue(PhotoAsset.supported("Photo.LIVP")); assertTrue(PhotoAsset.supported("motion.GIF"));
        assertFalse(PhotoAsset.supported("unpaired.mov"));
    }
    @Test public void gaussianIsSymmetricAndPreservesUniformColorWithoutMutation() {
        int[] input = new int[9]; java.util.Arrays.fill(input, 0xFF000000); input[4] = 0xFFFFFFFF;
        int[] output = PhotoPixels.gaussian(input, 9, 1);
        assertEquals(output[3], output[5]); assertEquals(output[2], output[6]);
        assertTrue((output[4] & 255) > (output[3] & 255)); assertEquals(0xFFFFFFFF, input[4]);
        java.util.Arrays.fill(input, 0xFF123456);
        assertArrayEquals(input, PhotoPixels.gaussian(input, 3, 3));
    }
}
