package com.kotea.companion.util;

import com.intellij.openapi.diagnostic.Logger;

public final class PerfLog {

    private PerfLog() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void logElapsed(Logger log, String label, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info(label + " in " + elapsedMs + " ms on " + Thread.currentThread().getName());
    }

    public static void warnIfSlow(Logger log, String label, long startNanos, long thresholdMs) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= thresholdMs) {
            log.warn(label + " took " + elapsedMs + " ms (>= " + thresholdMs + " ms threshold) on " + Thread.currentThread().getName());
        }
    }
}
