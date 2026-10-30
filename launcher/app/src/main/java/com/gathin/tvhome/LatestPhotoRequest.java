package com.gathin.tvhome;

/** One running decode and one replaceable destination, independent of how fast the remote repeats. */
final class LatestPhotoRequest<T> {
    private T wanted, running;
    private boolean closed;
    void request(T target) { if (!closed) wanted = target; }
    T wanted() { return wanted; }
    T start() {
        if (closed || running != null || wanted == null) return null;
        running = wanted; return running;
    }
    boolean complete(T result) {
        if (!java.util.Objects.equals(running, result)) throw new IllegalStateException("Unexpected photo completion");
        running = null; return !closed && java.util.Objects.equals(wanted, result);
    }
    void close() { closed = true; wanted = null; }
}
