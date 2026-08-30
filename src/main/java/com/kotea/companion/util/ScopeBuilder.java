package com.kotea.companion.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
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

    public static GlobalSearchScope getModuleScope(@NotNull PsiElement e) {
        Module module = ModuleUtilCore.findModuleForPsiElement(e);
        if (module == null) return GlobalSearchScope.EMPTY_SCOPE;
        return module.getModuleProductionSourceScope();
    }
}
