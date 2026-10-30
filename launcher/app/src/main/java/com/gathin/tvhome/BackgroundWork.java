package com.gathin.tvhome;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** CPU/USB work must yield to the remote, rendering and the user's video app. */
final class BackgroundWork {
    private BackgroundWork() { }
    static ExecutorService single(String name) {
        return make(name, new LinkedBlockingQueue<>());
    }
    static ExecutorService latest(String name) {
        return make(name, new java.util.concurrent.ArrayBlockingQueue<>(1));
    }
    private static ExecutorService make(String name, java.util.concurrent.BlockingQueue<Runnable> queue) {
        ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 20, TimeUnit.SECONDS,
                queue, job -> new Thread(() -> {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                    job.run();
                }, name), new ThreadPoolExecutor.DiscardOldestPolicy());
        return worker;
    }
}
