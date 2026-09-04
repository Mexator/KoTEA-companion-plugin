package com.kotea.companion.util;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for the Event/Command/News Emission and Processing searchers. Allows to cache search results.
 */
public abstract class CachingSearcher<T> {

    private final Key<CachedValue<Map<GlobalSearchScope, List<PsiElement>>>> cacheKey = Key.create(getClass().getName());

    protected final List<PsiElement> findCached(@NotNull T target, @NotNull GlobalSearchScope scope) {
        PsiElement anchor = cacheKeyOf(target);
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(anchor, cacheKey,
                () -> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                });

        return scopeMap.computeIfAbsent(scope, s -> search(target, s));
    }

    /** The PSI element {@link #findCached}'s results are stored on. */
    protected abstract PsiElement cacheKeyOf(@NotNull T target);

    protected abstract List<PsiElement> search(@NotNull T target, @NotNull GlobalSearchScope scope);
}
