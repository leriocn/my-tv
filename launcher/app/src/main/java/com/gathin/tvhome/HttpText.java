package com.gathin.tvhome;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

/** Bounded text download with a total deadline, not just a per-read timeout. No main-thread calls. */
final class HttpText {
    static String get(String address) throws IOException {
        return get(address, 65536, 12000);
    }
    static String get(String address, int maxBytes, int budgetMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);
        try {
            connection.setConnectTimeout(Math.min(4000, budgetMs)); connection.setReadTimeout(Math.min(4000, budgetMs));
            connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent", "MyTV-Personal/3.4");
            if (connection.getResponseCode() != 200) throw new IOException("Unavailable");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new InterruptedIOException("Request deadline");
                    if (out.size() + n > maxBytes) throw new IOException("Response too large");
                    out.write(buffer, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }
}
