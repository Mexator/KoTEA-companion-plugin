package com.kotea.companion.navigation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.util.PerfLog;
import com.kotea.companion.util.ScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EmissionOrProcessingSearcher {

    private static final Logger LOG = Logger.getInstance(EmissionOrProcessingSearcher.class);

    private static final Map<Role, Key<CachedValue<List<PsiElement>>>> CACHE_KEYS =
            new EnumMap<>(Map.of(
                    Role.EMISSION, Key.create("KoTEASearcher.EMISSION"),
                    Role.PROCESSING, Key.create("KoTEASearcher.PROCESSING")));

    private EmissionOrProcessingSearcher() {
    }

    /**
     * Finds emission or processing sites of a KoTEA class {@code target} in the project's production sources
     */
    public static List<PsiElement> find(@NotNull KtClassOrObject target, @NotNull Role role) {
        return CachedValuesManager.getCachedValue(target, CACHE_KEYS.get(role),
                () -> CachedValueProvider.Result.create(search(target, role), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static List<PsiElement> search(KtClassOrObject target, Role role) {
        long start = PerfLog.start();
        Project project = target.getProject();
        KoTEARoleResolver resolver = KoTEARoleResolver.getInstance(project);
        List<PsiElement> sites = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, ScopeBuilder.getProductionScope(project), false).findAll()) {
            PsiElement el = ref.getElement();
            if (resolver.roleOf(el) == role) sites.add(el);
        }
        PerfLog.logSearch(LOG, "KoTEASearcher " + role, target.getName(), sites.size(), start);
        return sites;
    }
}
