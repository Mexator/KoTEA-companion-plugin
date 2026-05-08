package com.plugin;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventEmissionSearcher {

    public static List<PsiElement> findEmissions(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(target,
                () -> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                });

        return scopeMap.computeIfAbsent(scope, s -> search(target, s));
    }

    private static List<PsiElement> search(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        // Narrow scope before search so the indexer skips Update files entirely.
        GlobalSearchScope emissionScope = new DelegatingGlobalSearchScope(scope) {
            @Override
            public boolean contains(@NotNull VirtualFile file) {
                return super.contains(file) && !file.getName().contains("Update");
            }
        };

        List<PsiElement> emissionPlaces = new ArrayList<>();
        for (var ref : ReferencesSearch.search(target, emissionScope, false).findAll()) {
            PsiElement el = ref.getElement();
            if (PsiTreeUtil.getParentOfType(el, KtImportDirective.class) != null) continue;
            if (PsiTreeUtil.getParentOfType(el, KtTypeReference.class) != null) continue;
            emissionPlaces.add(el);
        }
        return emissionPlaces;
    }
}