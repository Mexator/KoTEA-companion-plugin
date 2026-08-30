package com.kotea.companion.index;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.kotlin.idea.KotlinFileType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Owns up-to-date {@link KoTEARootsIndex} snapshot, and rebuilds it in the background.
 */
@Service(Service.Level.PROJECT)
public final class KoTEAIndexService implements Disposable {

    private static final Logger LOG = Logger.getInstance(KoTEAIndexService.class);

    private final Project project;

    private volatile KoTEARootsIndex snapshot = KoTEARootsIndex.EMPTY;
    private volatile boolean initialized = false;
    private volatile boolean fullRebuildRequested = false;
    private final AtomicLong changeCounter = new AtomicLong();
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

    private final ExecutorService recomputeExecutor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("KoTEA index recompute", 1);
    private final AtomicBoolean recomputePending = new AtomicBoolean(false);

    // Mutated only on the single-threaded recompute executor (see recomputeExecutor).
    private final Map<VirtualFile, List<UpdateRecord>> recordsByFile = new HashMap<>();

    private final Set<VirtualFile> dirtyFiles = ConcurrentHashMap.newKeySet();
    private final Set<VirtualFile> deletedFiles = ConcurrentHashMap.newKeySet();

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;
    }

    public void registerListeners() {

        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
            @Override
            protected void onChange(@Nullable PsiFile file) {
                if (file == null) return;
                VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile == null || !isRelevant(virtualFile)) return;
                dirtyFiles.add(virtualFile);
                onChangeDetected();
            }
        }, this);

        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file == null) continue;
                    if (event instanceof VFileDeleteEvent) {
                        if (!file.getFileType().equals(KotlinFileType.INSTANCE)) continue;
                        deletedFiles.add(file);
                    } else if (isRelevant(file)) {
                        dirtyFiles.add(file);
                    }
                }
                if (!dirtyFiles.isEmpty() || !deletedFiles.isEmpty()) onChangeDetected();
            }
        });

        project.getMessageBus().connect(this).subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
            @Override
            public void rootsChanged(@NotNull ModuleRootEvent event) {
                fullRebuildRequested = true;
                onChangeDetected();
            }
        });

        DumbService.getInstance(project).runWhenSmart(this::scheduleRecompute);
    }

    public static KoTEAIndexService getInstance(@NotNull Project project) {
        return project.getService(KoTEAIndexService.class);
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
     * Test hook: {@code true} when no rebuild is running or queued, i.e. every change observed so
     * far has been folded into {@link #getIndex()}. Production code reads {@link #getIndex()}
     * directly (it is stale-while-revalidating) and has no need for this.
     */
    @TestOnly
    public boolean isIdle() {
        return initialized && !recomputePending.get();
    }

    @Override
    public void dispose() {
        // Listeners are unregistered automatically (registered with `this` as parent disposable);
        // interrupt an in-flight rebuild and drop any queued one.
        recomputeExecutor.shutdownNow();
    }

    private boolean isRelevant(VirtualFile file) {
        return file.getFileType() == KotlinFileType.INSTANCE && ScopeBuilder.getProductionScope(project).contains(file);
    }

    private void onChangeDetected() {
        changeCounter.incrementAndGet();
        scheduleRecompute();
    }

    private void scheduleRecompute() {
        if (!recomputePending.compareAndSet(false, true)) return;
        try {
            recomputeExecutor.execute(this::recompute);
        } catch (RejectedExecutionException e) {
            // Executor shut down (project closing) - nothing left to do.
            recomputePending.set(false);
        }
    }

    private void recompute() {
        long stamp = changeCounter.get();
        try {
            if (fullRebuildRequested || !initialized) {
                runFull(stamp);
                return;
            }

            Set<VirtualFile> dirtySnapshot = Set.copyOf(dirtyFiles);
            Set<VirtualFile> deletedSnapshot = Set.copyOf(deletedFiles);

            Map<VirtualFile, List<UpdateRecord>> patched = ReadAction.nonBlocking(
                            () -> computeIncrementalPatch(dirtySnapshot, deletedSnapshot))
                    .inSmartMode(project)
                    .expireWith(this)
                    .executeSynchronously();

            if (patched == null) {
                runFull(stamp);
                return;
            }

            long recomputeStart = PerfLog.start();
            for (Map.Entry<VirtualFile, List<UpdateRecord>> entry : patched.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    recordsByFile.remove(entry.getKey());
                } else {
                    recordsByFile.put(entry.getKey(), entry.getValue());
                }
            }
            for (VirtualFile file : deletedSnapshot) recordsByFile.remove(file);

            publish(
                    KoTEAIndexComputer.derive(recordsByFile.values()),
                    stamp,
                    "incremental (" + dirtySnapshot.size() + " files)",
                    recomputeStart
            );
        } finally {
            recomputePending.set(false);
            if (changeCounter.get() != stamp || !initialized) scheduleRecompute();
        }
    }

    private @Nullable Map<VirtualFile, List<UpdateRecord>> computeIncrementalPatch(
            Set<VirtualFile> dirtySnapshot,
            Set<VirtualFile> deletedSnapshot
    ) {
        Map<VirtualFile, List<UpdateRecord>> patched = new HashMap<>();
        for (VirtualFile file : dirtySnapshot) {
            List<UpdateRecord> newRecords = KoTEAIndexComputer.computeForFile(project, file);
            if (!sameUpdateHierarchy(recordsByFile.getOrDefault(file, List.of()), newRecords)) {
                return null;
            }
            patched.put(file, newRecords);
        }
        for (VirtualFile file : deletedSnapshot) {
            List<UpdateRecord> oldRecords = recordsByFile.get(file);
            if (oldRecords != null && !oldRecords.isEmpty()) {
                return null;
            }
        }
        return patched;
    }

    private void runFull(long stamp) {
        long recomputeStart = PerfLog.start();

        BackgroundableProcessIndicator indicator =
                new BackgroundableProcessIndicator(project, "Indexing KoTEA events/commands", null, null, true);
        Ref<Map<VirtualFile, List<UpdateRecord>>> result = new Ref<>();
        // runProcess owns the indicator's start/stop lifecycle (and so the status-bar widget's
        // teardown); wrapProgress makes the read action observe that same indicator's cancellation.
        ProgressManager.getInstance().runProcess(
                () -> result.set(ReadAction.nonBlocking(() -> KoTEAIndexComputer.computeAll(project))
                        .inSmartMode(project)
                        .expireWith(this)
                        .wrapProgress(indicator)
                        .executeSynchronously()),
                indicator
        );

        recordsByFile.clear();
        recordsByFile.putAll(result.get());
        publish(KoTEAIndexComputer.derive(recordsByFile.values()), stamp, "full", recomputeStart);
    }

    private void publish(KoTEARootsIndex newIndex, long stamp, String pathLabel, long recomputeStart) {
        boolean changed = !newIndex.equals(snapshot);
        if (changed) {
            snapshot = newIndex;
            modificationTracker.incModificationCount();
        }
        initialized = true;

        int recordCount = recordsByFile.values().stream().mapToInt(List::size).sum();
        PerfLog.logElapsed(LOG, "KoTEA index recompute (" + pathLabel + "), " + recordsByFile.size() + " files / "
                + recordCount + " records, snapshot " + (changed ? "changed" : "unchanged"), recomputeStart);

        if (changed) {
            ApplicationManager.getApplication().invokeLater(
                    () -> DaemonCodeAnalyzer.getInstance(project).restart("KoTEA index changed"),
                    project.getDisposed()
            );
        }

        if (changeCounter.get() == stamp) {
            dirtyFiles.clear();
            deletedFiles.clear();
            fullRebuildRequested = false;
        }
    }

    /**
     * @return true if oldRecords and newRecords have the same Updates. Checks if Update FQNs did not change as well
     * as their ancestors.
     */
    private static boolean sameUpdateHierarchy(List<UpdateRecord> oldRecords, List<UpdateRecord> newRecords) {
        Set<UpdateHierarchy> oldKeys = oldRecords.stream().map(UpdateHierarchy::new).collect(Collectors.toSet());
        Set<UpdateHierarchy> newKeys = newRecords.stream().map(UpdateHierarchy::new).collect(Collectors.toSet());
        return oldKeys.equals(newKeys);
    }

    private record UpdateHierarchy(@Nullable String fqn, Set<String> updateAncestorFqns) {
        UpdateHierarchy(UpdateRecord record) {
            this(record.fqn(), record.updateAncestorFqns());
        }
    }
}
