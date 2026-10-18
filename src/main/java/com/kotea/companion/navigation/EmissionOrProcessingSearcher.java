package com.kotea.companion.navigation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.domain.KoTEARoleResolver;
import com.kotea.companion.domain.KoTEARoleResolver.Role;
import com.kotea.companion.util.PerfLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EmissionOrProcessingSearcher {

    private static final Logger LOG = Logger.getInstance(EmissionOrProcessingSearcher.class);

    private static final Map<Role, Key<CachedValue<Map<GlobalSearchScope, List<PsiElement>>>>> CACHE_KEYS =
            new EnumMap<>(Map.of(
                    Role.EMISSION, Key.create("KoTEASearcher.EMISSION"),
                    Role.PROCESSING, Key.create("KoTEASearcher.PROCESSING")));

    private EmissionOrProcessingSearcher() {
    }

    /**
     * Finds emission or processing sites of a KoTEA class {@code target} in a given {@code scope}
     */
    public static List<PsiElement> find(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope,
                                        @NotNull Role role) {
        Map<GlobalSearchScope, List<PsiElement>> byScope = CachedValuesManager.getCachedValue(target,
                CACHE_KEYS.get(role), () -> CachedValueProvider.Result.create(new ConcurrentHashMap<>(),
                        PsiModificationTracker.MODIFICATION_COUNT));
        return byScope.computeIfAbsent(scope, s -> search(target, s, role));
    }

    private static List<PsiElement> search(KtClassOrObject target, GlobalSearchScope scope, Role role) {
        long start = PerfLog.start();
        KoTEARoleResolver resolver = KoTEARoleResolver.getInstance(target.getProject());
        List<PsiElement> sites = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (resolver.roleOf(el) == role) sites.add(el);
        }
        PerfLog.logSearch(LOG, "KoTEASearcher " + role, target.getName(), sites.size(), start);
        return sites;
    }
}
