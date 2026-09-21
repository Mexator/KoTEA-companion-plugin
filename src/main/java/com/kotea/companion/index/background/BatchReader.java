package com.kotea.companion.index.background;

import com.kotea.companion.index.IndexLog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongFunction;

/**
 * Runs read-only work in the background, one read action per {@link #read}, and adds up their timing. A write
 * action cancels the read that is running; it is then run again.
 * <p>
 * When batched, each read gets a deadline about 250 ms ahead, so a write loses only that batch. Otherwise it
 * gets no deadline, and the whole work runs in one read.
 */
public final class BatchReader {

    static final long BATCH_BUDGET_NANOS = 250_000_000L;

    /**
     * @param lostMs time spent in reads that a write cancelled
     * @param waitMs the rest: waiting for writes to finish and for smart mode
     */
    public record Stats(int batches, int cancelled, long lostMs, long waitMs) {
    }

    private final Project project;
    private final Disposable parent;
    private final String label;
    private final boolean batched;
    @Nullable
    private final ProgressIndicator indicator;

    private int batches;
    private int cancelled;
    private long workNanos;
    private long lostNanos;
    private long waitNanos;

    public BatchReader(Project project, Disposable parent, String label, boolean batched,
                       @Nullable ProgressIndicator indicator) {
        this.project = project;
        this.parent = parent;
        this.label = label;
        this.batched = batched;
        this.indicator = indicator;
    }

    /**
     * Runs {@code batch} in a read action in smart mode and returns what it gave. {@code batch} gets the deadline
     * to stop at, and must not change any state: a write may cancel it, and it then runs again.
     */
    public <T> T read(LongFunction<T> batch) {
        int batchNo = ++batches;
        NonBlockingReadAction<T> action = ReadAction.nonBlocking(() -> {
                    long attemptStart = System.nanoTime();
                    try {
                        T result = batch.apply(batched ? attemptStart + BATCH_BUDGET_NANOS : Long.MAX_VALUE);
                        workNanos += System.nanoTime() - attemptStart;
                        return result;
                    } catch (ProcessCanceledException e) {
                        long lost = System.nanoTime() - attemptStart;
                        lostNanos += lost;
                        cancelled++;
                        IndexLog.debug(() -> label + " batch " + batchNo + " cancelled by a write after ~"
                                + lost / 1_000_000 + " ms of work");
                        throw e;
                    }
                })
                .inSmartMode(project)
                .expireWith(parent);
        if (indicator != null) action = action.wrapProgress(indicator);

        long spentBefore = workNanos + lostNanos;
        long submitted = System.nanoTime();
        T result = action.executeSynchronously();
        waitNanos += System.nanoTime() - submitted - (workNanos + lostNanos - spentBefore);
        return result;
    }

    public Stats stats() {
        return new Stats(batches, cancelled, lostNanos / 1_000_000, waitNanos / 1_000_000);
    }
}
