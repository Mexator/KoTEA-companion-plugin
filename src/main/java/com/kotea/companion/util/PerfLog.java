package com.kotea.companion.util;

import com.intellij.openapi.diagnostic.Logger;

public final class PerfLog {

    private PerfLog() {
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void logElapsed(Logger log, String label, long startNanos) {
        if (log.isDebugEnabled()) {
            log.debug(label + " in " + elapsedMs(startNanos) + " ms on " + Thread.currentThread().getName());
        }
    }

    public static void logSearch(Logger log, String searcherName, String target, int resultCount, long startNanos) {
        if (log.isDebugEnabled()) {
            log.debug(searcherName + " search for " + target + " found " + resultCount + " results in "
                    + elapsedMs(startNanos) + " ms on " + Thread.currentThread().getName());
        }
    }

    public static void warnIfSlow(Logger log, String label, long startNanos, long thresholdMs) {
        long elapsedMs = elapsedMs(startNanos);
        if (elapsedMs >= thresholdMs) {
            log.warn(label + " took " + elapsedMs + " ms (>= " + thresholdMs + " ms threshold) on "
                    + Thread.currentThread().getName());
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
