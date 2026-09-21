package com.kotea.companion.index;

import com.kotea.companion.index.background.BatchReader;
import com.kotea.companion.index.model.FileEntry;
import com.kotea.companion.index.model.UpdateHierarchy;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the up-to-date {@link KoTEARootsIndex} snapshot. The index is built by one full scan when the project
 * opens; after that, each change re-reads only the files it can affect.
 */
@Service(Service.Level.PROJECT)
public final class KoTEAIndexService implements Disposable {

    private static final int MAX_ROUNDS = 50;

    private final Project project;
    private final boolean batchedReads = Registry.is("kotea.index.batched.reads", false);

    private volatile KoTEARootsIndex snapshot = KoTEARootsIndex.EMPTY;
    private volatile boolean initialized = false;
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

    private final ExecutorService worker =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("KoTEA index", 1);
    private final AtomicBoolean workPending = new AtomicBoolean(false);
    private final AtomicBoolean scanRequested = new AtomicBoolean(false);
    private final AtomicBoolean listenersRegistered = new AtomicBoolean(false);
    private final AtomicLong changeCounter = new AtomicLong();
    private volatile boolean updateClassMissing = false;

    private final ChangeCollector changes = new ChangeCollector();
    // Mutated only on the single-threaded worker.
    private final UpdateHierarchy hierarchy = new UpdateHierarchy();

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;
    }

    public static KoTEAIndexService getInstance(@NotNull Project project) {
        return project.getService(KoTEAIndexService.class);
    }

    /** Starts listening to Kotlin file changes. */
    public void registerListeners() {
        if (!listenersRegistered.compareAndSet(false, true)) return;

        changes.register(project, this, this::onChangeDetected);

        project.getMessageBus().connect(this).subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
            @Override
            public void rootsChanged(@NotNull ModuleRootEvent event) {
                // Root changes matter only until KoTEA itself shows up on the classpath, e.g. after the first import.
                if (updateClassMissing) scanProject();
            }
        });
    }

    /**
     * Builds the whole index from scratch. Called once when the project opens. Changes that arrive while it runs
     * are applied after it finishes.
     */
    public void scanProject() {
        scanRequested.set(true);
        onChangeDetected();
    }

    public KoTEARootsIndex getIndex() {
        return snapshot;
    }

    /**
     * Bumped only when {@link #getIndex()}'s result actually changes - unlike
     * {@link com.intellij.psi.util.PsiModificationTracker#MODIFICATION_COUNT}, which bumps on
     * every PSI edit project-wide.
     */
    public ModificationTracker getModificationTracker() {
        return modificationTracker;
    }

    /**
     * Test hook: {@code true} when no work is running or queued, i.e. every change observed so
     * far has been folded into {@link #getIndex()}. Production code reads {@link #getIndex()}
     * directly (it is stale-while-revalidating) and has no need for this.
     */
    @TestOnly
    public boolean isIdle() {
        return initialized && !workPending.get();
    }

    @Override
    public void dispose() {
        worker.shutdownNow();
    }

    private void onChangeDetected() {
        changeCounter.incrementAndGet();
        scheduleWork();
    }

    private void scheduleWork() {
        if (!workPending.compareAndSet(false, true)) return;
        submitWork();
    }

    private void submitWork() {
        try {
            worker.execute(this::doWork);
        } catch (RejectedExecutionException e) {
            // Worker shut down (project closing) - nothing left to do.
            workPending.set(false);
        }
    }

    private void doWork() {
        long stamp = changeCounter.get();
        try {
            if (scanRequested.getAndSet(false)) {
                runScan();
            } else if (initialized) {
                runCycle();
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Throwable t) {
            initialized = true;
            IndexLog.error("KoTEA index update failed", t);
        } finally {
            if (!worker.isShutdown() && (changeCounter.get() != stamp || scanRequested.get())) {
                submitWork();
            } else {
                workPending.set(false);
                if (changeCounter.get() != stamp || scanRequested.get()) scheduleWork();
            }
        }
    }

    private void runScan() {
        long start = System.nanoTime();
        changes.clear();
        DumbService.getInstance(project).waitForSmartMode();

        UpdateScan scan = UpdateScan.fromKoTEABases(project);
        ScanTimings.reset();
        BackgroundableProcessIndicator indicator =
                new BackgroundableProcessIndicator(project, "Indexing KoTEA events/commands", null, null, false);
        BatchReader reader = new BatchReader(project, this, "Scan", batchedReads, indicator);
        // runProcess owns the indicator's start/stop lifecycle (and so the status-bar widget's teardown).
        ProgressManager.getInstance().runProcess(() -> {
            while (!scan.isDone()) {
                UpdateScan.Batch batch = reader.read(scan::compute);
                scan.apply(batch);
                IndexLog.debug(() -> "Scan batch " + reader.stats().batches() + " finished: " + scan.describe(batch));
            }
        }, indicator);

        updateClassMissing = scan.updateClassMissing();
        hierarchy.replaceAll(scan.entries());
        initialized = true;
        boolean changed = publish();
        BatchReader.Stats stats = reader.stats();
        IndexLog.debug(() -> "Scan done: " + stats.batches() + " batches, " + stats.cancelled()
                + " cancelled by writes, " + scan.candidatesChecked() + " candidates checked, "
                + hierarchy.recordCount() + " Updates in " + hierarchy.fileCount() + " files, " + IndexLog.ms(start)
                + " ms total (" + timing(stats) + "), result "
                + (changed ? "changed" : "unchanged") + (updateClassMissing ? ", KoTEA not on the classpath" : ""));
        IndexLog.debug(ScanTimings::summary);
    }

    /**
     * Applies the recorded changes: re-reads the changed files, then keeps re-reading the files those changes
     * can affect until nothing changes any more.
     */
    private void runCycle() {
        long start = System.nanoTime();
        ChangeCollector.Changes drained = changes.drain();
        if (drained.isEmpty()) return;

        CycleStats stats = new CycleStats();
        BatchReader reader = new BatchReader(project, this, "Cycle", batchedReads, null);
        Map<String, FileEntry> updates = hierarchy.removalsUnder(drained.removedPaths());
        Set<VirtualFile> toRead = drained.files();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (toRead.isEmpty() && updates.isEmpty()) break;
            updates.putAll(readFiles(toRead, round == 0, reader, stats));
            UpdateHierarchy.HierarchyChanged changed = hierarchy.apply(updates);
            stats.changed.addAll(changed.fqns());

            // A file read earlier in this cycle is read again when a parent changes: its records are built
            // from the parent's record.
            toRead = hierarchy.filesDescendingFrom(changed.fqns());
            if (!changed.added().isEmpty()) toRead.addAll(walkFrom(changed.added(), reader, stats));
            updates = new LinkedHashMap<>();
        }

        boolean changed = publish();
        IndexLog.debug(() -> "Cycle: " + drained.eventCount() + " dirty, " + stats.changed.size() + " records changed "
                + IndexLog.maskAll(stats.changed) + ", " + stats.skipped + " files skipped by supertype names, "
                + stats.reread + " files re-read, " + stats.walks + " walks, result "
                + (changed ? "changed" : "unchanged") + ", " + IndexLog.ms(start) + " ms (" + timing(reader.stats()) + ")");
    }

    private String timing(BatchReader.Stats stats) {
        return (batchedReads ? "batched reads, " : "one read, ") + stats.cancelled() + " cancelled by writes, "
                + stats.lostMs() + " ms lost to writes, " + stats.waitMs() + " ms waiting for writes and smart mode";
    }

    private static final class CycleStats {
        final Set<String> changed = new HashSet<>();
        int skipped;
        int reread;
        int walks;
    }

    /**
     * @param firstRound the files changed themselves, so a file not yet indexed is skipped when it names no known
     *                   Update in its supertype lists; later rounds re-read indexed descendants and new subclasses
     */
    private Map<String, FileEntry> readFiles(Set<VirtualFile> files, boolean firstRound, BatchReader reader,
                                             CycleStats stats) {
        ChangedFileReads reads = new ChangedFileReads(project, files, hierarchy.recordsByFqn(), hierarchy.paths(),
                firstRound ? hierarchy.shortNames() : null);
        while (!reads.isDone()) reads.apply(reader.read(reads::compute));
        stats.skipped += reads.skipped();
        if (!firstRound) stats.reread += reads.fileCount();
        return reads.entries();
    }

    /** Finds the classes that extend {@code fqns}, which became Updates in this cycle, and returns their files. */
    private Set<VirtualFile> walkFrom(Set<String> fqns, BatchReader reader, CycleStats stats) {
        stats.walks += fqns.size();
        UpdateScan walk = UpdateScan.fromClasses(project, fqns, hierarchy.recordsByFqn());
        while (!walk.isDone()) walk.apply(reader.read(walk::compute));
        Set<VirtualFile> files = new HashSet<>();
        for (FileEntry entry : walk.entries().values()) files.add(entry.file());
        return files;
    }

    /** @return whether the published Roots changed */
    private boolean publish() {
        KoTEARootsIndex newIndex = KoTEAIndexComputer.derive(hierarchy.recordsByFile());
        if (newIndex.equals(snapshot)) return false;

        snapshot = newIndex;
        modificationTracker.incModificationCount();
        ApplicationManager.getApplication().invokeLater(
                () -> DaemonCodeAnalyzer.getInstance(project).restart("KoTEA index changed"),
                project.getDisposed()
        );
        return true;
    }
}
