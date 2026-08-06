package com.kotea.companion.index;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class KoTEAIndexService {

    private final Project project;
    private final CachedValue<KoTEAIndex> cache;

    public KoTEAIndexService(@NotNull Project project) {
        this.project = project;
        this.cache = CachedValuesManager.getManager(project).createCachedValue(() ->
                CachedValueProvider.Result.create(
                        KoTEAIndexComputer.compute(project),
                        PsiModificationTracker.MODIFICATION_COUNT
                ), false);
    }

    public static KoTEAIndexService getInstance(@NotNull Project project) {
        return project.getService(KoTEAIndexService.class);
    }

    public KoTEAIndex getIndex() {
        if (DumbService.getInstance(project).isDumb()) return KoTEAIndex.EMPTY;
        return cache.getValue();
    }
}
