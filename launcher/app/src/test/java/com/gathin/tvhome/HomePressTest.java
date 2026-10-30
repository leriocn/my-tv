package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomePressTest {
    @Test public void ordinaryClickAndLongPressUseTheSameFreshGesture() {
        HomePress press = new HomePress();
        assertTrue(press.accept(23,100,true,false,false));
        assertTrue(press.accept(23,100,true,true,false));
        assertTrue(press.accept(23,100,false,false,false));
        assertFalse(press.accept(23,100,false,false,false));
    }
    @Test public void returnToHomeCannotCompleteAnOldApplicationPress() {
        HomePress press = new HomePress();
        assertTrue(press.accept(23,100,true,false,false)); press.reset();
        assertFalse(press.accept(23,100,true,true,false));
        assertFalse(press.accept(23,100,false,false,false));
        assertTrue(press.accept(23,200,true,false,false));
        assertTrue(press.accept(23,200,false,false,false));
    }
    @Test public void wrongReleaseAndCanceledGestureNeverActivate() {
        HomePress press = new HomePress(); press.accept(23,100,true,false,false);
        assertFalse(press.accept(66,100,false,false,false));
        press.accept(23,200,true,false,false);
        assertFalse(press.accept(23,200,false,false,true));
        assertFalse(press.accept(23,200,false,false,false));
    }
}
