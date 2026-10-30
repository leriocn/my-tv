package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhotoPixelsTest {
    @Test public void solidColorRemainsUnchangedAtEdges() {
        int[] pixels = new int[64 * 36];
        java.util.Arrays.fill(pixels, 0xFF52749A);
        assertArrayEquals(pixels, PhotoPixels.blur(pixels, 64, 36, 3));
    }
    @Test public void blurAveragesChannelsWithoutMutatingInput() {
        int[] pixels = {0xFFFF0000, 0xFF0000FF};
        assertArrayEquals(new int[]{0xFF7F007F, 0xFF7F007F}, PhotoPixels.blur(pixels, 2, 1, 1));
        assertEquals(0xFFFF0000, pixels[0]);
    }
    @Test public void invalidDimensionsAndHugeRadiusAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PhotoPixels.blur(new int[1], 2, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> PhotoPixels.blur(new int[1], 1, 1, 99));
    }
    @Test public void zeroRadiusKeepsOpaquePixels() {
        assertArrayEquals(new int[]{0xFF123456}, PhotoPixels.blur(new int[]{0xFF123456}, 1, 1, 0));
    }
}
