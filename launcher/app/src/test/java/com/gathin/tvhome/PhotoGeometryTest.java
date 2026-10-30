package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoGeometryTest {
    @Test public void retainedAlbumImagesFitTheirVisibleAreaWithoutUpscaling() {
        int[][] sources = {{3024,4032},{4032,3024},{720,1280},{400,300},{65535,20},{20,65535}};
        for (int[] p : sources) {
            int[] out = PhotoGeometry.retainedSize(p[0], p[1], 1920, 1080);
            assertTrue(out[0] <= p[0]); assertTrue(out[1] <= p[1]);
            assertTrue(out[0] <= Math.ceil(1920 * .94));
            assertTrue(out[1] <= Math.ceil(1080 * .92));
            double scaleW = out[0] / (double) p[0], scaleH = out[1] / (double) p[1];
            assertTrue(Math.abs(scaleW - scaleH) <= 1d / p[0] + 1d / p[1]);
        }
        assertArrayEquals(new int[]{400,300}, PhotoGeometry.retainedSize(400,300,1920,1080));
        assertThrows(IllegalArgumentException.class, () -> PhotoGeometry.retainedSize(0,300,1920,1080));
    }
    @Test public void albumBackdropNeedsOnlyTwentyKiBRatherThanEightMiB() {
        assertEquals(20736, PhotoGeometry.BACKDROP_W * PhotoGeometry.BACKDROP_H * 4);
        assertTrue(PhotoGeometry.BACKDROP_W * PhotoGeometry.BACKDROP_H < 1920 * 1080 / 300);
    }
    @Test public void portraitFitsWithoutCuttingOffTopOrBottom() {
        float[] bounds = PhotoGeometry.bounds(900, 1600, 1920, 1080, true);
        assertArrayEquals(new float[]{656.25f, 0f, 1263.75f, 1080f}, bounds, .01f);
    }
    @Test public void landscapeCoversScreenWithoutStretching() {
        float[] r = PhotoGeometry.bounds(4000, 3000, 1920, 1080, false);
        assertArrayEquals(new float[]{0, -180, 1920, 1260}, r, .01f);
        assertEquals(4f / 3, (r[2] - r[0]) / (r[3] - r[1]), .001f);
    }
    @Test public void exactAspectRatioDoesNotCrop() {
        assertArrayEquals(new float[]{0, 0, 1920, 1080}, PhotoGeometry.bounds(3840, 2160, 1920, 1080, false), .01f);
    }
    @Test public void tallAndWideImagesRemainWithinDecodeBudget() {
        int[][] photos = {{12000, 9000}, {65535, 2159}, {2000, 60000}, {4032, 3024}, {720, 1280}};
        for (int[] p : photos) {
            int sample = PhotoGeometry.sample(p[0], p[1], 1920, 1080);
            assertEquals(0, sample & (sample - 1));
            assertTrue((long) (p[0] / sample) * (p[1] / sample) <= 4_200_000L);
            assertTrue(Math.max(p[0] / sample, p[1] / sample) <= 3840);
        }
    }
    @Test public void smallPhotosKeepTheirOriginalDetail() {
        assertEquals(1, PhotoGeometry.sample(900, 1600, 1920, 1080));
    }
    @Test public void albumStartsOnlyAfterDeadlineOnVisibleFocusedHome() {
        assertFalse(PhotoGeometry.due(1000, 300999, 300000, true, true));
        assertTrue(PhotoGeometry.due(1000, 301000, 300000, true, true));
        assertFalse(PhotoGeometry.due(1000, 400000, 300000, false, true));
        assertFalse(PhotoGeometry.due(1000, 400000, 300000, true, false));
    }
    @Test public void newInteractionAndDisabledTimerPreventAlbum() {
        assertFalse(PhotoGeometry.due(390000, 400000, 300000, true, true));
        assertFalse(PhotoGeometry.due(1000, 400000, 0, true, true));
        assertFalse(PhotoGeometry.due(1000, 500, 300000, true, true));
    }
    @Test public void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhotoGeometry.bounds(0, 1600, 1920, 1080, true));
    }
}
