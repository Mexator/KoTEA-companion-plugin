package com.kotea.companion.index;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.ModificationTracker;
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
import org.jetbrains.kotlin.idea.KotlinFileType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Stateful, event-driven replacement for the old {@code CachedValue}: {@link #getIndex()} always
 * returns the latest published {@link KoTEAIndex} with zero blocking work, while PSI/VFS/roots
 * listeners mark files dirty and a coalesced background {@link com.intellij.openapi.application.NonBlockingReadAction}
 * keeps {@link #recordsByFile} - and therefore the published snapshot - up to date.
 */
@Service(Service.Level.PROJECT)
public final class KoTEAIndexService implements Disposable {

    private static final Logger LOG = Logger.getInstance(KoTEAIndexService.class);

    /** Above this many dirty files in one batch (e.g. a VCS checkout), a full rebuild is cheaper
     * than that many individual per-file scans. */
    private static final int FULL_REBUILD_THRESHOLD = 50;

    private final Project project;

    private volatile KoTEAIndex snapshot = KoTEAIndex.EMPTY;
    private volatile boolean initialized = false;
    private volatile boolean fullRebuildRequested = false;
    private final AtomicLong changeCounter = new AtomicLong();
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

    // Mutated only on the (single, coalesced) recompute background thread.
    private final Map<VirtualFile, List<UpdateRecord>> recordsByFile = new HashMap<>();

    private final Set<VirtualFile> dirtyFiles = ConcurrentHashMap.newKeySet();
    private final Set<VirtualFile> deletedFiles = ConcurrentHashMap.newKeySet();

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;

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
                boolean anyRelevant = false;
                for (VFileEvent event : events) {
                    VirtualFile file = event.getFile();
                    if (file == null || !isRelevant(file)) continue;
                    anyRelevant = true;
                    if (event instanceof VFileDeleteEvent) {
                        deletedFiles.add(file);
                    } else {
                        dirtyFiles.add(file);
                    }
                }
                if (anyRelevant) onChangeDetected();
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

    /** Always non-blocking: the volatile snapshot, possibly stale by one recompute cycle. */
    public KoTEAIndex getIndex() {
        if (DumbService.getInstance(project).isDumb()) return KoTEAIndex.EMPTY;
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

    public void requestFullRebuild() {
        fullRebuildRequested = true;
        onChangeDetected();
    }

    @Override
    public void dispose() {
        // Listeners are unregistered automatically (registered with `this` as parent disposable).
    }

    private boolean isRelevant(VirtualFile file) {
        return file.getFileType() == KotlinFileType.INSTANCE && ScopeBuilder.getProductionScope(project).contains(file);
    }

    private void onChangeDetected() {
        changeCounter.incrementAndGet();
        scheduleRecompute();
    }

    private void scheduleRecompute() {
        ReadAction.nonBlocking(this::recompute)
                .inSmartMode(project)
                .expireWith(this)
                .coalesceBy(this)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void recompute() {
        long stamp = changeCounter.get();
        Set<VirtualFile> dirtySnapshot = Set.copyOf(dirtyFiles);
        Set<VirtualFile> deletedSnapshot = Set.copyOf(deletedFiles);

        boolean doFullRebuild = fullRebuildRequested || !initialized || dirtySnapshot.size() > FULL_REBUILD_THRESHOLD;

        // Membership check: patch every dirty file locally, but if any file's Feature-Update
        // membership signature changed (an Update implementation was added/removed/renamed/
        // re-parented), that can flip the leaf status of subclasses in files we didn't scan, so
        // fall back to a full rebuild in the same pass instead of leaving a stale patch.
        Map<VirtualFile, List<UpdateRecord>> patched = null;
        if (!doFullRebuild) {
            patched = new HashMap<>();
            for (VirtualFile file : dirtySnapshot) {
                List<UpdateRecord> newRecords = KoTEAIndexComputer.computeForFile(project, file);
                if (!sameMembership(recordsByFile.get(file), newRecords)) {
                    doFullRebuild = true;
                    break;
                }
                patched.put(file, newRecords);
            }
            if (!doFullRebuild) {
                for (VirtualFile file : deletedSnapshot) {
                    List<UpdateRecord> oldRecords = recordsByFile.get(file);
                    if (oldRecords != null && !oldRecords.isEmpty()) {
                        doFullRebuild = true;
                        break;
                    }
                }
            }
        }

        long recomputeStart = PerfLog.start();
        String path;
        if (doFullRebuild) {
            Map<VirtualFile, List<UpdateRecord>> all = KoTEAIndexComputer.computeAll(project);
            recordsByFile.clear();
            recordsByFile.putAll(all);
            path = "full";
        } else {
            for (Map.Entry<VirtualFile, List<UpdateRecord>> entry : patched.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    recordsByFile.remove(entry.getKey());
                } else {
                    recordsByFile.put(entry.getKey(), entry.getValue());
                }
            }
            for (VirtualFile file : deletedSnapshot) recordsByFile.remove(file);
            path = "incremental (" + dirtySnapshot.size() + " files)";
        }

        KoTEAIndex newIndex = KoTEAIndexComputer.derive(recordsByFile.values());
        boolean changed = !newIndex.equals(snapshot);
        if (changed) {
            snapshot = newIndex;
            modificationTracker.incModificationCount();
        }
        initialized = true;

        int recordCount = recordsByFile.values().stream().mapToInt(List::size).sum();
        PerfLog.logElapsed(LOG, "KoTEA index recompute (" + path + "), " + recordsByFile.size() + " files / "
                + recordCount + " records, snapshot " + (changed ? "changed" : "unchanged"), recomputeStart);

        if (changed) {
            ApplicationManager.getApplication().invokeLater(
                    () -> DaemonCodeAnalyzer.getInstance(project).restart(), project.getDisposed());
        }

        // Only clear what this run consumed if nothing new arrived while we were computing -
        // otherwise a write-action cancellation mid-flight would lose the edits it caused, since
        // the already-scheduled next run is the only thing that will ever see them.
        if (changeCounter.get() == stamp) {
            dirtyFiles.clear();
            deletedFiles.clear();
            fullRebuildRequested = false;
        }
    }

    private static boolean sameMembership(@Nullable List<UpdateRecord> oldRecords, List<UpdateRecord> newRecords) {
        Set<UpdateRecord.MembershipKey> oldKeys = oldRecords == null ? Set.of() :
                oldRecords.stream().map(UpdateRecord::membershipKey).collect(Collectors.toSet());
        Set<UpdateRecord.MembershipKey> newKeys = newRecords.stream().map(UpdateRecord::membershipKey).collect(Collectors.toSet());
        return oldKeys.equals(newKeys);
    }
}
