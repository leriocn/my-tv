package com.gathin.tvhome;

/** Tokens make late prepare/completion/timer callbacks harmless after navigation, pause or exit. */
final class PoetrySequence {
    static final long MEANING_GAP_MS = 3_000;
    static final long NEXT_GAP_MS = 5_000;
    enum Phase { RECITATION, WAIT_MEANING, MEANING, WAIT_NEXT }
    private Phase phase = Phase.RECITATION;
    private long generation;
    private boolean active;
    long begin() { active=true; return ++generation; }
    void cancel() { active=false; generation++; }
    boolean accepts(long token) { return active && generation==token; }
    Phase phase() { return phase; }
    void reset() { cancel(); phase=Phase.RECITATION; }
    void audioCompleted() {
        if(phase==Phase.RECITATION)phase=Phase.WAIT_MEANING;
        else if(phase==Phase.MEANING)phase=Phase.WAIT_NEXT;
        else throw new IllegalStateException("Not playing audio");
    }
    long delayMs() { return phase==Phase.WAIT_MEANING?MEANING_GAP_MS:phase==Phase.WAIT_NEXT?NEXT_GAP_MS:0; }
    boolean finishWait() {
        if(phase==Phase.WAIT_MEANING){phase=Phase.MEANING;return false;}
        if(phase==Phase.WAIT_NEXT){phase=Phase.RECITATION;return true;}
        throw new IllegalStateException("Not waiting");
    }
}
