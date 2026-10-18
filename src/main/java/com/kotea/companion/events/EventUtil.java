package com.kotea.companion.events;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.kotea.companion.domain.KoTEAClassifier;
import com.kotea.companion.util.KoTEAElementKind;
import com.kotea.companion.util.KoTEAClassSupport;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClassOrObject;

public class EventUtil {

    /**
     * Returns true if passed ktClass is an Event class for which this plugin provides navigation actions
     */
    public static boolean isNavigableEventClass(KtClassOrObject ktClass) {
        if (KoTEAClassifier.getInstance(ktClass.getProject()).kindOf(ktClass) != KoTEAElementKind.EVENT) return false;
        PsiClass lightClass = LightClassUtilsKt.toLightClass(ktClass);
        return lightClass != null && !lightClass.isInterface() && !lightClass.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    @Nullable
    public static KtClassOrObject tryResolveToClass(PsiElement element) {
        KtClassOrObject candidate = KoTEAClassSupport.resolveClassOrObject(element);
        return candidate != null && isNavigableEventClass(candidate) ? candidate : null;
    }
}
