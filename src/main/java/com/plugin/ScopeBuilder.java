package com.plugin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import org.bouncycastle.math.raw.Mod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class ScopeBuilder {
    public static GlobalSearchScope getProductionScope(@NotNull PsiElement e) {
        Project project = e.getProject();
        ProjectScopeBuilder builder = ProjectScopeBuilder.getInstance(project);

        GlobalSearchScope baseScope = builder.buildContentScope();

        return new GlobalSearchScope() {
            @Override
            public boolean isSearchInModuleContent(@NotNull Module module) {
                return true;
            }

            @Override
            public boolean isSearchInLibraries() {
                return false;
            }

            @Override
            public boolean contains(@NotNull VirtualFile virtualFile) {
                return baseScope.contains(virtualFile) && !TestSourcesFilter.isTestSources(virtualFile, project);
            }
        };
    }

    public static GlobalSearchScope getTestScope(@NotNull PsiElement e) {
        Project project = e.getProject();
        ProjectScopeBuilder builder = ProjectScopeBuilder.getInstance(project);
        GlobalSearchScope baseScope = builder.buildContentScope();
        return new GlobalSearchScope() {
            @Override
            public boolean isSearchInModuleContent(@NotNull Module module) {
                return true;
            }

            @Override
            public boolean isSearchInLibraries() {
                return false;
            }

            @Override
            public boolean contains(@NotNull VirtualFile virtualFile) {
                return baseScope.contains(virtualFile) && TestSourcesFilter.isTestSources(virtualFile, project);
            }
        };
    }

    public static GlobalSearchScope getModuleScope(@NotNull PsiElement e) {
        Module module = ModuleUtilCore.findModuleForPsiElement(e);
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module);

        return new GlobalSearchScope() {
            @Override
            public boolean isSearchInModuleContent(@NotNull Module _module) {
                return _module.equals(module);
            }

            @Override
            public boolean isSearchInLibraries() {
                return false;
            }

            @Override
            public boolean contains(@NotNull VirtualFile virtualFile) {
                return moduleScope.contains(virtualFile) && !TestSourcesFilter.isTestSources(virtualFile, e.getProject());
            }
        };
    }
}
