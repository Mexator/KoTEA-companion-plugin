package com.kotea.companion.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import org.jetbrains.annotations.NotNull;

public class ScopeBuilder {
    public static GlobalSearchScope getProductionScope(@NotNull PsiElement e) {
        return getProductionScope(e.getProject());
    }

    public static GlobalSearchScope getProductionScope(@NotNull Project project) {
        return GlobalSearchScopes.projectProductionScope(project);
    }

    /**
     * Production sources without generated code: the only place where Updates and their Roots can live.
     */
    public static GlobalSearchScope getUpdateSourcesScope(@NotNull Project project) {
        return new DelegatingGlobalSearchScope(getProductionScope(project)) {
            @Override
            public boolean contains(@NotNull VirtualFile file) {
                return super.contains(file) && !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project);
            }
        };
    }

    public static GlobalSearchScope getModuleScope(@NotNull PsiElement e) {
        Module module = ModuleUtilCore.findModuleForPsiElement(e);
        if (module == null) return GlobalSearchScope.EMPTY_SCOPE;
        return module.getModuleProductionSourceScope();
    }
}
