package com.plugin;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtImportDirective;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.kotlin.psi.KtParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventProcessingSearcher {

    public static List<PsiElement> findProcessing(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(target,
                () -> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                });

        return scopeMap.computeIfAbsent(scope, s -> search(target, s));
    }

    private static List<PsiElement> search(@NotNull KtClassOrObject target, @NotNull GlobalSearchScope scope) {
        List<PsiElement> results = new ArrayList<>();

        for (PsiReference ref : ReferencesSearch.search(target, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            VirtualFile file = el.getContainingFile().getVirtualFile();
            if (file == null || !scope.contains(file)) continue;

            if (!file.getName().contains("Update")) continue;

            if (PsiTreeUtil.getParentOfType(el, KtImportDirective.class) != null) continue;
            if (PsiTreeUtil.getParentOfType(el, KtParameter.class) != null) continue;

            results.add(el);
        }
        return results;
    }
}