package com.gathin.tvhome;

/** An OK release may activate a tile only if this Home received that same fresh press. */
final class HomePress {
    private int key = -1;
    private long began;
    boolean accept(int code, long downTime, boolean down, boolean repeat, boolean canceled) {
        if (canceled) { reset(); return false; }
        if (down && !repeat) { key = code; began = downTime; return true; }
        boolean same = key == code && began == downTime;
        if (!down) reset();
        return same;
    }
    void reset() { key = -1; began = 0; }
}
