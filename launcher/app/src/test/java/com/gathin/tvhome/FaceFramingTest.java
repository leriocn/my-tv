package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class FaceFramingTest {
    @Test public void portraitFaceMovesAboveAppsWhileCoveringEveryEdge() {
        float[] f={.30f,.30f,.65f,.38f};
        float[] r=FaceFraming.place(1200,2000,1920,1080,1,f);
        assertCover(r); assertFace(r,f,1080*.205f,1080*.69f);
        assertFalse(java.util.Arrays.equals(r,PhotoGeometry.bounds(1200,2000,1920,1080,false)));
    }
    @Test public void groupThatCannotAvoidTwoRowsIsStillKeptOnScreen() {
        float[] f={.1f,.25f,.9f,.45f};
        float[] r=FaceFraming.place(1200,2000,1920,1080,2,f);
        assertCover(r); assertFace(r,f,0,1080);
    }
    @Test public void distantHeadsNeverCauseLetterboxingOrShrinkTheOriginal() {
        float[] r=FaceFraming.place(1200,2000,1920,1080,2,new float[]{.1f,.02f,.9f,.95f});
        assertCover(r); assertEquals(3200,r[3]-r[1],.001f);
    }
    @Test public void landscapeWithoutFacesKeepsClearCenteredCoverLayout() {
        assertArrayEquals(PhotoGeometry.bounds(1920,1080,1920,1080,false),
                FaceFraming.place(1920,1080,1920,1080,1,new float[0]),.001f);
    }
    @Test public void undetectedPortraitUsesCenteredCrop() {
        float[] r=FaceFraming.place(1200,2000,1920,1080,2,new float[0]);
        assertArrayEquals(new float[]{0,-1060,1920,2140},r,.001f);
    }
    @Test public void headsAtImageEndsRemainVisibleWithoutExposingEdges() {
        for(float[] f:new float[][]{{.3f,0,.7f,.2f},{.3f,.8f,.7f,1}}) {
            float[] r=FaceFraming.place(1200,2000,1920,1080,2,f);
            assertCover(r); assertFace(r,f,0,1080);
        }
    }
    private static void assertCover(float[] r) {
        assertTrue(r[0]<=.01f && r[1]<=.01f && r[2]>=1919.99f && r[3]>=1079.99f);
        assertEquals(1200f/2000,(r[2]-r[0])/(r[3]-r[1]),.0001f);
    }
    private static void assertFace(float[] r,float[] f,float top,float bottom) {
        float w=r[2]-r[0],h=r[3]-r[1];
        assertTrue(r[0]+f[0]*w>=-.05f); assertTrue(r[0]+f[2]*w<=1920.05f);
        assertTrue(r[1]+f[1]*h>=top-.05f); assertTrue(r[1]+f[3]*h<=bottom+.05f);
    }
}
