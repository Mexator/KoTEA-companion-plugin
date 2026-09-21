package com.kotea.companion.events;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.kotea.companion.index.KoTEAIndexService;
import com.kotea.companion.util.KoTEAClassSupport;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;

public class EventUtil {

    /**
     * Returns true if passed ktClass is an Event class for which this plugin provides navigation actions
     */
    public static boolean isNavigableEventClass(KtClassOrObject ktClass) {
        if (DumbService.getInstance(ktClass.getProject()).isDumb()) return false;
        return CachedValuesManager.getCachedValue(ktClass, () ->
                CachedValueProvider.Result.create(computeIsNavigableEventClass(ktClass),
                        PsiModificationTracker.MODIFICATION_COUNT,
                        KoTEAIndexService.getInstance(ktClass.getProject()).getModificationTracker()));
    }

    private static boolean computeIsNavigableEventClass(KtClassOrObject ktClass) {
        PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
        if (lightClass == null) return false;
        if (lightClass.isInterface() || lightClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
        return KoTEAIndexService.getInstance(ktClass.getProject()).getIndex().isEvent(lightClass);
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        KtClassOrObject candidate = KoTEAClassSupport.resolveClassOrObject(element);
        return candidate != null && isNavigableEventClass(candidate) ? candidate : null;
    }
}
