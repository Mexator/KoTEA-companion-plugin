package com.kotea.companion.index;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Time spent in each step of checking a scan candidate, for the debug log. Includes batches that a write
 * cancelled.
 */
public final class ScanTimings {

    public enum Step {
        LOOKUP("candidate lookup"),
        SUPERTYPE("supertype check"),
        RECORD("record"),
        FALLBACK("resolve fallback");

        final String label;
        final LongAdder nanos = new LongAdder();
        final LongAdder calls = new LongAdder();

        Step(String label) {
            this.label = label;
        }
    }

    private record Slow(String fqn, long nanos) {
    }

    private static final int SLOWEST = 5;
    private static final List<Slow> slowest = new ArrayList<>();

    private ScanTimings() {
    }

    public static <T> T time(Step step, Supplier<T> body) {
        long start = System.nanoTime();
        try {
            return body.get();
        } finally {
            step.nanos.add(System.nanoTime() - start);
            step.calls.increment();
        }
    }

    static synchronized void candidateChecked(String fqn, long nanos) {
        if (slowest.size() == SLOWEST && slowest.getLast().nanos() >= nanos) return;
        slowest.add(new Slow(IndexLog.mask(fqn), nanos));
        slowest.sort(Comparator.comparingLong(Slow::nanos).reversed());
        if (slowest.size() > SLOWEST) slowest.removeLast();
    }

    static synchronized void reset() {
        for (Step step : Step.values()) {
            step.nanos.reset();
            step.calls.reset();
        }
        slowest.clear();
    }

    static synchronized String summary() {
        StringBuilder result = new StringBuilder("Scan steps:");
        for (Step step : Step.values()) {
            result.append(' ').append(step.label).append(' ').append(step.nanos.sum() / 1_000_000)
                    .append(" ms (").append(step.calls.sum()).append(" calls),");
        }
        result.append(" slowest candidates:");
        for (Slow slow : slowest) result.append(' ').append(slow.fqn()).append(' ').append(slow.nanos() / 1_000_000).append(" ms,");
        if (result.charAt(result.length() - 1) == ',') result.setLength(result.length() - 1);
        return result.toString();
    }
}
