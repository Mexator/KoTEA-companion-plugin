package com.kotea.companion.commands;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandEmissionSearcher {

    public static List<PsiElement> findEmission(@NotNull UClass uClass, GlobalSearchScope scope) {
        Map<GlobalSearchScope, List<PsiElement>> scopeMap = CachedValuesManager.getCachedValue(uClass.getJavaPsi(),
                ()-> {
                    Map<GlobalSearchScope, List<PsiElement>> map = new ConcurrentHashMap<>();
                    return CachedValueProvider.Result.create(map, PsiModificationTracker.MODIFICATION_COUNT);
                } );

        return scopeMap.computeIfAbsent(scope, s -> search(uClass, s));
    }

    private static List<PsiElement> search(@NotNull UClass uClass, GlobalSearchScope scope) {
        GlobalSearchScope emissionScope = new DelegatingGlobalSearchScope(scope) {
            @Override
            public boolean contains(@NotNull VirtualFile file) {
                return super.contains(file) && !file.getName().contains("Handler");
            }
        };

        List<PsiElement> targets = new ArrayList<>();
        PsiClass psiClass = uClass.getJavaPsi();

        ReferencesSearch.search(psiClass, emissionScope).forEach(usage -> {
            PsiElement element = usage.getElement();
            UElement uElement = UastContextKt.toUElement(element, UElement.class);
            if (uElement == null) return true;

            if (UastUtils.getParentOfType(uElement, UImportStatement.class) != null) return true;
            if (UastUtils.getParentOfType(uElement, UTypeReferenceExpression.class) != null ||
                    UastUtils.getParentOfType(uElement, UAnnotation.class) != null) return true;

            UCallExpression call = UastUtils.getParentOfType(uElement, UCallExpression.class, false);
            if (call != null) {
                targets.add(element);
                return true;
            }

            if (uElement instanceof USimpleNameReferenceExpression || uElement instanceof UQualifiedReferenceExpression) {
                UElement parent = uElement.getUastParent();
                if (parent instanceof UVariable variable) {
                    if (!uElement.equals(variable.getTypeReference())) {
                        targets.add(element);
                    }
                }
            }
            return true;
        });

        return targets;
    }
}
