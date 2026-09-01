package com.kotea.companion.news;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.index.KoTEAIndexService;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

/**
 * Recognises Concrete News - a concrete class whose Nearest Root is a News Root. News navigation is
 * not implemented yet, so this is groundwork for it; there is no News marker provider or searcher.
 */
public final class NewsUtil {

    private NewsUtil() {
    }

    public static boolean isNews(@Nullable PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return isNewsClass(psiClass);
        }
        UClass uClass = UastContextKt.toUElement(element, UClass.class);
        return uClass != null && isNewsClass(uClass.getJavaPsi());
    }

    private static boolean isNewsClass(@Nullable PsiClass psiClass) {
        if (psiClass == null) return false;
        if (DumbService.getInstance(psiClass.getProject()).isDumb()) return false;
        return CachedValuesManager.getCachedValue(psiClass, () ->
                CachedValueProvider.Result.create(computeIsNewsClass(psiClass),
                        PsiModificationTracker.MODIFICATION_COUNT,
                        KoTEAIndexService.getInstance(psiClass.getProject()).getModificationTracker()));
    }

    private static boolean computeIsNewsClass(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
        return KoTEAIndexService.getInstance(psiClass.getProject()).getIndex().isNews(psiClass);
    }
}
