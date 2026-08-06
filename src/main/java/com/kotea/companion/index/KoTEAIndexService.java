package com.kotea.companion.index;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class KoTEAIndexService {

    private static final Logger LOG = Logger.getInstance(KoTEAIndexService.class);

    private final Project project;
    private final CachedValue<KoTEAIndex> cache;

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;
        this.cache = CachedValuesManager.getManager(project).createCachedValue(() -> {
            long start = System.nanoTime();
            KoTEAIndex index = KoTEAIndexComputer.compute(project);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            LOG.info("KoTEA index rebuilt in " + elapsedMs + " ms");
            return CachedValueProvider.Result.create(index, PsiModificationTracker.MODIFICATION_COUNT);
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
