package com.kotea.companion.index;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.indexing.FileBasedIndex;
import com.kotea.companion.util.PerfLog;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class KoTEAIndexService {

    private static final Logger LOG = Logger.getInstance(KoTEAIndexService.class);

    private final Project project;
    private final CachedValue<KoTEAIndex> cache;

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;
        // Correctly content-sensitive (confirmed against platform source,
        // MapInputDataDiffBuilder.differentiate()): bumps only when a file's supertype-name data
        // actually changes, i.e. exactly when something starts or stops extending something in the
        // Update hierarchy - not on every PSI edit project-wide.
        ModificationTracker superTypeIndexTracker = () ->
                FileBasedIndex.getInstance().getIndexModificationStamp(KoTEASuperTypeNameIndex.NAME, project);
        this.cache = CachedValuesManager.getManager(project).createCachedValue(() -> {
            long start = PerfLog.start();
            KoTEAIndex index = KoTEAIndexComputer.compute(project);
            PerfLog.logElapsed(LOG, "KoTEA index rebuild", start);
            return CachedValueProvider.Result.create(
                    index, superTypeIndexTracker, ProjectRootModificationTracker.getInstance(project));
        }, false);
    }

    public static KoTEAIndexService getInstance(@NotNull Project project) {
        return project.getService(KoTEAIndexService.class);
    }

    public KoTEAIndex getIndex() {
        if (DumbService.getInstance(project).isDumb()) return KoTEAIndex.EMPTY;
        return cache.getValue();
    }
}
